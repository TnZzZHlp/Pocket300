package com.yamibo.pocket300.data.download

import android.content.Context
import com.yamibo.pocket300.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-level, single-consumer post download queue.
 *
 * Requests are persisted before being offered to the in-memory channel. A leftover request is
 * resumed when a new repository instance starts, and a leftover request whose final manifest is
 * already valid is acknowledged without downloading again.
 */
class PostDownloadRepository internal constructor(
    private val store: PostDownloadFileStore,
    private val imageDownloader: PostImageDownloader,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val queue = Channel<PostDownloadKey>(Channel.UNLIMITED)
    private val cancelledKeys = ConcurrentHashMap.newKeySet<PostDownloadKey>()
    private val activeTasks = ConcurrentHashMap<PostDownloadKey, Job>()
    private val mutationMutex = Mutex()
    private val _downloads = MutableStateFlow<List<DownloadedPost>>(emptyList())
    private val _statuses = MutableStateFlow<Map<PostDownloadKey, PostDownloadStatus>>(emptyMap())

    val downloads: StateFlow<List<DownloadedPost>> = _downloads.asStateFlow()
    val statuses: StateFlow<Map<PostDownloadKey, PostDownloadStatus>> = _statuses.asStateFlow()

    private val initialization: Deferred<Unit>
    private val processor: Job

    init {
        initialization = scope.async(Dispatchers.IO) { initializeFromDisk() }
        processor = scope.launch(Dispatchers.IO) {
            initialization.await()
            for (key in queue) {
                supervisorScope {
                    val task = launch(start = CoroutineStart.LAZY) { process(key) }
                    activeTasks[key] = task
                    task.start()
                    try {
                        task.join()
                    } finally {
                        activeTasks.remove(key, task)
                    }
                }
            }
        }
    }

    suspend fun awaitInitialized() {
        initialization.await()
    }

    suspend fun enqueue(request: PostDownloadRequest) {
        initialization.await()
        var shouldQueue = false
        mutationMutex.withLock {
            val active = _statuses.value[request.key]?.phase
            if (
                active != PostDownloadPhase.QUEUED &&
                active != PostDownloadPhase.DOWNLOADING
            ) {
                val completed = withContext(Dispatchers.IO) { store.read(request.key) }
                if (completed != null) {
                    refreshFromDiskLocked()
                } else {
                    withContext(Dispatchers.IO) { store.persistRequest(request) }
                    cancelledKeys.remove(request.key)
                    publishStatus(request.queuedStatus())
                    shouldQueue = true
                }
            }
        }
        if (shouldQueue) queue.send(request.key)
    }

    suspend fun retry(key: PostDownloadKey): Boolean {
        initialization.await()
        var found = false
        var shouldQueue = false
        mutationMutex.withLock {
            val request = withContext(Dispatchers.IO) { store.loadQueuedRequest(key) }
                ?: _statuses.value[key]?.request
            if (request != null) {
                found = true
                val active = _statuses.value[key]?.phase
                if (
                    active != PostDownloadPhase.QUEUED &&
                    active != PostDownloadPhase.DOWNLOADING
                ) {
                    withContext(Dispatchers.IO) { store.persistRequest(request) }
                    cancelledKeys.remove(key)
                    publishStatus(request.queuedStatus())
                    shouldQueue = true
                }
            }
        }
        if (shouldQueue) queue.send(key)
        return found
    }

    suspend fun read(key: PostDownloadKey): DownloadedPost? {
        initialization.await()
        return withContext(Dispatchers.IO) { store.read(key) }
    }

    suspend fun listCompleted(): List<DownloadedPost> {
        initialization.await()
        return withContext(Dispatchers.IO) { store.listCompleted() }
    }

    suspend fun refresh() {
        initialization.await()
        mutationMutex.withLock {
            refreshFromDiskLocked()
        }
    }

    suspend fun delete(key: PostDownloadKey) {
        initialization.await()
        withContext(NonCancellable) {
            mutationMutex.withLock {
                cancelledKeys += key
                try {
                    activeTasks[key]?.cancelAndJoin()
                    val previous = _statuses.value[key]
                    val deleted = withContext(Dispatchers.IO) { store.delete(key) }
                    if (deleted) {
                        _statuses.update { it - key }
                        refreshFromDiskLocked()
                    } else {
                        refreshFromDiskLocked()
                        if (_statuses.value[key]?.completed == null) {
                            previous?.request?.let { request ->
                                publishStatus(
                                    request.failedStatus(
                                        IOException("Could not remove the post download"),
                                    ),
                                )
                            }
                        }
                        throw IOException("Could not remove the post download")
                    }
                } finally {
                    cancelledKeys.remove(key)
                }
            }
        }
    }

    suspend fun deleteAll() {
        initialization.await()
        withContext(NonCancellable) {
            mutationMutex.withLock {
                val previous = _statuses.value
                cancelledKeys += previous.keys
                try {
                    val tasks = activeTasks.values.toList()
                    tasks.forEach { it.cancel() }
                    tasks.forEach { it.join() }
                    val deleted = withContext(Dispatchers.IO) { store.deleteAll() }
                    if (deleted) {
                        _statuses.value = emptyMap()
                        _downloads.value = emptyList()
                    } else {
                        refreshFromDiskLocked()
                        previous.forEach { (key, status) ->
                            if (_statuses.value[key]?.completed == null) {
                                status.request?.let { request ->
                                    publishStatus(
                                        request.failedStatus(
                                            IOException("Could not remove all post downloads"),
                                        ),
                                    )
                                }
                            }
                        }
                        throw IOException("Could not remove all post downloads")
                    }
                } finally {
                    cancelledKeys.removeAll(previous.keys.toSet())
                }
            }
        }
    }

    internal fun close() {
        initialization.cancel()
        processor.cancel()
        queue.close()
    }

    private suspend fun initializeFromDisk() {
        val completed: List<DownloadedPost>
        val queued: List<PostDownloadRequest>
        val failed: List<PostDownloadRequest>
        withContext(Dispatchers.IO) {
            check(store.cleanupStaging()) { "Could not remove stale download staging files" }
            check(store.cleanupQueueArtifacts()) { "Could not remove stale download queue files" }
            completed = store.listCompletedAndCleanupInvalid()
            queued = store.loadQueuedRequests()
            failed = store.loadFailedRequests()
        }
        _downloads.value = completed
        val completeByKey = completed.associateBy(DownloadedPost::key)
        val initialStatuses = completed.associate { it.key to it.completedStatus() }.toMutableMap()
        failed.forEach { request ->
            if (request.key in completeByKey) {
                withContext(Dispatchers.IO) { store.removeQueuedRequest(request.key) }
            } else {
                initialStatuses[request.key] = request.failedStatus(
                    IOException("Previous post download failed"),
                )
            }
        }
        queued.forEach { request ->
            val alreadyComplete = completeByKey[request.key]
            if (alreadyComplete != null) {
                withContext(Dispatchers.IO) { store.removeQueuedRequest(request.key) }
            } else {
                initialStatuses[request.key] = request.queuedStatus()
                queue.send(request.key)
            }
        }
        _statuses.value = initialStatuses
    }

    private suspend fun process(key: PostDownloadKey) {
        if (cancelledKeys.remove(key)) return
        var request: PostDownloadRequest? = null
        var staging: PostDownloadStaging? = null
        try {
            val activeRequest =
                withContext(Dispatchers.IO) { store.loadQueuedRequest(key) } ?: return
            request = activeRequest
            var alreadyComplete = false
            mutationMutex.withLock {
                if (withContext(Dispatchers.IO) { store.read(key) } != null) {
                    withContext(Dispatchers.IO) { store.removeQueuedRequest(key) }
                    refreshFromDiskLocked()
                    alreadyComplete = true
                }
            }
            if (alreadyComplete) return

            val activeStaging = withContext(Dispatchers.IO) { store.createStaging(key) }
            staging = activeStaging
            val images = ArrayList<PostDownloadImage>(activeRequest.remoteImageUrls.size)
            var downloadedBytes = 0L
            publishStatus(activeRequest.downloadingStatus(0, downloadedBytes))
            activeRequest.remoteImageUrls.forEachIndexed { index, remoteUrl ->
                checkNotCancelled(key)
                val imageFile = activeStaging.imageFile(index)
                val result = imageDownloader.download(
                    PostImageDownloadRequest(remoteUrl, activeRequest.referer),
                    imageFile,
                )
                check(imageFile.isFile && imageFile.length() == result.byteCount) {
                    "Downloaded image size did not match the response"
                }
                downloadedBytes += result.byteCount
                images += PostDownloadImage(
                    remoteUrl = remoteUrl,
                    relativePath = "${PostDownloadStaging.IMAGE_DIRECTORY_NAME}/${imageFile.name}",
                    byteCount = result.byteCount,
                    sha256 = withContext(Dispatchers.IO) { fileSha256(imageFile) },
                    contentType = result.contentType,
                )
                publishStatus(activeRequest.downloadingStatus(index + 1, downloadedBytes))
            }
            checkNotCancelled(key)
            mutationMutex.withLock {
                checkNotCancelled(key)
                withContext(Dispatchers.IO) {
                    store.commit(activeStaging, activeRequest, images, completedAt = clock())
                    store.removeQueuedRequest(key)
                }
                refreshFromDiskLocked()
            }
        } catch (error: PostDownloadCancelledException) {
            staging?.let { withContext(Dispatchers.IO) { store.discard(it) } }
            _statuses.update { it - key }
        } catch (error: CancellationException) {
            staging?.let {
                withContext(NonCancellable + Dispatchers.IO) { store.discard(it) }
            }
            throw error
        } catch (error: Exception) {
            staging?.let { withContext(Dispatchers.IO) { store.discard(it) } }
            if (key in cancelledKeys) {
                _statuses.update { it - key }
            } else {
                AppLogger.warn(TAG, error) {
                    "Post download failed; threadId=${key.threadId}, postId=${key.postId}"
                }
                (request ?: _statuses.value[key]?.request)?.let { failedRequest ->
                    try {
                        mutationMutex.withLock {
                            withContext(Dispatchers.IO) {
                                store.persistFailedRequest(failedRequest)
                            }
                            publishStatus(failedRequest.failedStatus(error))
                        }
                    } catch (persistenceError: CancellationException) {
                        throw persistenceError
                    } catch (persistenceError: Exception) {
                        AppLogger.warn(TAG, persistenceError) {
                            "Could not persist failed post download state; " +
                                "threadId=${key.threadId}, postId=${key.postId}"
                        }
                        publishStatus(failedRequest.failedStatus(error))
                    }
                }
            }
        } finally {
            cancelledKeys.remove(key)
        }
    }

    private fun checkNotCancelled(key: PostDownloadKey) {
        if (key in cancelledKeys) throw PostDownloadCancelledException()
    }

    private fun publishStatus(status: PostDownloadStatus) {
        _statuses.update { it + (status.key to status) }
    }

    private suspend fun refreshFromDiskLocked() {
        val completed =
            withContext(Dispatchers.IO) { store.listCompletedAndCleanupInvalid() }
        _downloads.value = completed
        val completedKeys = completed.mapTo(mutableSetOf(), DownloadedPost::key)
        _statuses.update { current ->
            buildMap {
                current
                    .filter { (key, status) ->
                        key !in completedKeys && status.phase != PostDownloadPhase.COMPLETED
                    }
                    .forEach(::put)
                completed.forEach { put(it.key, it.completedStatus()) }
            }
        }
    }

    private class PostDownloadCancelledException : Exception()

    companion object {
        private const val TAG = "PostDownload"
        private const val ROOT_DIRECTORY_NAME = "post-downloads"

        @Volatile
        private var instance: PostDownloadRepository? = null

        fun getInstance(context: Context): PostDownloadRepository = instance ?: synchronized(this) {
            instance ?: createApplicationRepository(context.applicationContext).also { instance = it }
        }

        private fun createApplicationRepository(context: Context): PostDownloadRepository {
            val root = File(context.noBackupFilesDir, ROOT_DIRECTORY_NAME)
            return PostDownloadRepository(
                store = PostDownloadFileStore(
                    rootDirectory = root,
                    decoderValidator = AndroidPostDownloadImageDecoderValidator,
                ),
                imageDownloader = OkHttpPostImageDownloader(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
        }
    }
}

private fun PostDownloadRequest.queuedStatus(): PostDownloadStatus = PostDownloadStatus(
    key = key,
    phase = PostDownloadPhase.QUEUED,
    snapshot = snapshot,
    hasText = hasText,
    hasImages = hasImages,
    progress = PostDownloadProgress(0, remoteImageUrls.size, 0),
    request = this,
)

private fun PostDownloadRequest.downloadingStatus(
    completedImages: Int,
    downloadedBytes: Long,
): PostDownloadStatus = PostDownloadStatus(
    key = key,
    phase = PostDownloadPhase.DOWNLOADING,
    snapshot = snapshot,
    hasText = hasText,
    hasImages = hasImages,
    progress = PostDownloadProgress(completedImages, remoteImageUrls.size, downloadedBytes),
    request = this,
)

private fun PostDownloadRequest.failedStatus(error: Exception): PostDownloadStatus =
    PostDownloadStatus(
        key = key,
        phase = PostDownloadPhase.FAILED,
        snapshot = snapshot,
        hasText = hasText,
        hasImages = hasImages,
        progress = PostDownloadProgress(0, remoteImageUrls.size, 0),
        error = error.message ?: "Post download failed",
        request = this,
    )

private fun DownloadedPost.completedStatus(): PostDownloadStatus = PostDownloadStatus(
    key = key,
    phase = PostDownloadPhase.COMPLETED,
    snapshot = snapshot,
    hasText = manifest.hasText,
    hasImages = manifest.hasImages,
    progress = PostDownloadProgress(
        completedImages = manifest.images.size,
        totalImages = manifest.images.size,
        downloadedBytes = manifest.images.sumOf(PostDownloadImage::byteCount),
    ),
    completed = this,
)
