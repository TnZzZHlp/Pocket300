package com.yamibo.pocket300.data.download

import android.content.Context
import com.yamibo.pocket300.Pocket300Application
import com.yamibo.pocket300.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The single application entry point for offline-thread downloads.
 *
 * Callers only enqueue or manage requests here. [ThreadDownloadService] owns the lifetime of the
 * running worker, while [ThreadDownloadRepository] keeps the durable queue and atomic files.
 */
class ThreadDownloadManager internal constructor(
    private val repository: ThreadDownloadRepository,
    private val serviceController: ThreadDownloadServiceController,
    private val scope: CoroutineScope,
) {
    val downloads: StateFlow<List<DownloadedThread>> = repository.downloads
    val statuses: StateFlow<Map<ThreadDownloadKey, ThreadDownloadStatus>> = repository.statuses
    val queueState: StateFlow<ThreadDownloadQueueState> = repository.queueState

    @Volatile
    private var serviceRequested = false
    private val serviceLifecycleMutex = Mutex()
    private var activeServiceGeneration = 0L

    suspend fun awaitInitialized() {
        repository.awaitInitialized()
    }

    /** Persists the request, then asks the download service to process the queue. */
    suspend fun enqueue(request: ThreadDownloadRequest) {
        val wasIdle = queueState.value.orderedKeys.isEmpty()
        repository.enqueue(request)
        startServiceIfReady(forceStart = wasIdle)
    }

    /** Enqueues only when no completed or active download exists for the thread. */
    suspend fun enqueueIfMissing(request: ThreadDownloadRequest): Boolean {
        val wasIdle = queueState.value.orderedKeys.isEmpty()
        val enqueued = repository.enqueueIfMissing(request)
        if (enqueued) startServiceIfReady(forceStart = wasIdle)
        return enqueued
    }

    suspend fun retry(key: ThreadDownloadKey): Boolean {
        val wasIdle = queueState.value.orderedKeys.isEmpty()
        val found = repository.retry(key)
        if (found) startServiceIfReady(forceStart = wasIdle)
        return found
    }

    suspend fun pauseDownloads(): Boolean {
        val paused = repository.pauseDownloads()
        if (paused) {
            serviceRequested = false
            serviceController.stop()
        }
        return paused
    }

    suspend fun resumeDownloads(): Boolean {
        val resumed = repository.resumeDownloads()
        if (resumed) startServiceIfReady(forceStart = true)
        return resumed
    }

    suspend fun prioritize(key: ThreadDownloadKey): Boolean = repository.prioritize(key)

    suspend fun read(key: ThreadDownloadKey): DownloadedThread? = repository.read(key)

    suspend fun listCompleted(): List<DownloadedThread> = repository.listCompleted()

    suspend fun refresh() {
        repository.refresh()
    }

    suspend fun delete(key: ThreadDownloadKey) {
        repository.delete(key)
        stopServiceIfIdle()
    }

    suspend fun deleteAll() {
        repository.deleteAll()
        stopServiceIfIdle()
    }

    /** Restarts durable pending work after the app has returned to the foreground. */
    fun resumePendingDownloads() {
        scope.launch {
            awaitInitialized()
            startServiceIfReady()
        }
    }

    /**
     * Starts a new service generation after Android has accepted the foreground-service request.
     * A generation prevents a stale service destruction callback from stopping newer work.
     */
    internal suspend fun downloaderStart(): Long = serviceLifecycleMutex.withLock {
        activeServiceGeneration = if (activeServiceGeneration == Long.MAX_VALUE) {
            1L
        } else {
            activeServiceGeneration + 1L
        }
        repository.start()
        serviceRequested = true
        activeServiceGeneration
    }

    /** Called only by [ThreadDownloadService] as it is being stopped or destroyed. */
    internal fun downloaderStop(generation: Long) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serviceLifecycleMutex.withLock {
                if (generation != activeServiceGeneration) return@withLock
                serviceRequested = false
                repository.stop()
            }
        }
    }

    /** Persists a paused state before Android stops a data-sync service for its time limit. */
    internal fun pauseForServiceTimeout(generation: Long) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serviceLifecycleMutex.withLock {
                if (generation == activeServiceGeneration) {
                    repository.pauseDownloads()
                }
            }
        }
    }

    internal fun close() {
        repository.close()
    }

    private fun startServiceIfReady(forceStart: Boolean = false) {
        val state = queueState.value
        if (!state.isPaused && state.orderedKeys.isNotEmpty() && (forceStart || !serviceRequested)) {
            serviceRequested = serviceController.start()
        }
    }

    private fun stopServiceIfIdle() {
        if (queueState.value.orderedKeys.isEmpty()) {
            serviceRequested = false
            serviceController.stop()
        }
    }

    companion object {
        private const val ROOT_DIRECTORY_NAME = "thread-downloads"
        private const val LEGACY_ROOT_DIRECTORY_NAME = "post-downloads"

        @Volatile
        private var instance: ThreadDownloadManager? = null

        fun getInstance(context: Context): ThreadDownloadManager =
            instance ?: synchronized(this) {
                instance ?: createApplicationManager(context.applicationContext)
                    .also { instance = it }
            }

        private fun createApplicationManager(context: Context): ThreadDownloadManager {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val noBackupRoot = context.noBackupFilesDir
            return ThreadDownloadManager(
                repository = ThreadDownloadRepository(
                    store = ThreadDownloadFileStore(
                        rootDirectory = File(noBackupRoot, ROOT_DIRECTORY_NAME),
                        decoderValidator = AndroidThreadDownloadImageDecoderValidator,
                    ),
                    threadPostsSource = YamiboThreadPostsSource(Pocket300Application.api.posts),
                    imageDownloader = OkHttpPostImageDownloader(),
                    scope = scope,
                    legacyRootDirectory = File(noBackupRoot, LEGACY_ROOT_DIRECTORY_NAME),
                    autoStart = false,
                ),
                serviceController = AndroidThreadDownloadServiceController(context),
                scope = scope,
            )
        }
    }
}

/** Starts and stops the foreground component that owns actual queue execution. */
internal fun interface ThreadDownloadServiceController {
    fun start(): Boolean

    fun stop() {}
}

internal class AndroidThreadDownloadServiceController(context: Context) :
    ThreadDownloadServiceController {
    private val appContext = context.applicationContext

    override fun start(): Boolean = try {
        appContext.startForegroundService(ThreadDownloadService.intent(appContext))
        true
    } catch (error: RuntimeException) {
        AppLogger.warn(TAG, error) {
            "Could not start the foreground thread-download service; work remains queued"
        }
        false
    }

    override fun stop() {
        appContext.stopService(ThreadDownloadService.intent(appContext))
    }

    private companion object {
        const val TAG = "ThreadDownloadManager"
    }
}
