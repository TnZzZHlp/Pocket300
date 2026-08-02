package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboThreadDetails
import com.yamibo.pocket300.api.YamiboThreadPoll
import com.yamibo.pocket300.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Durable single-consumer engine for complete thread downloads.
 *
 * [ThreadDownloadManager] controls when its worker is dispatched through the foreground service.
 * Queue records are durable before work is offered to the channel. A refresh builds and validates
 * a new immutable version while the prior completed version remains readable.
 */
internal class ThreadDownloadRepository internal constructor(
    private val store: ThreadDownloadFileStore,
    private val threadPostsSource: ThreadPostsSource,
    private val imageDownloader: PostImageDownloader,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val legacyRootDirectory: File? = null,
    private val autoStart: Boolean = true,
    private val onEntryDequeued: suspend (ThreadDownloadKey) -> Unit = {},
) {
    private val downloader = ThreadDownloader(store, threadPostsSource, imageDownloader)
    private val downloadQueue = ThreadDownloadQueue()
    private val cancelledKeys = ConcurrentHashMap.newKeySet<ThreadDownloadKey>()
    private val activeTasks = ConcurrentHashMap<ThreadDownloadKey, Job>()
    private val mutationMutex = Mutex()
    private val _downloads = MutableStateFlow<List<DownloadedThread>>(emptyList())
    private val _statuses =
        MutableStateFlow<Map<ThreadDownloadKey, ThreadDownloadStatus>>(emptyMap())

    val downloads: StateFlow<List<DownloadedThread>> = _downloads.asStateFlow()
    val statuses: StateFlow<Map<ThreadDownloadKey, ThreadDownloadStatus>> =
        _statuses.asStateFlow()
    val queueState: StateFlow<ThreadDownloadQueueState> = downloadQueue.state

    private val initialization: Deferred<Unit>
    private val processorMutex = Mutex()
    @Volatile
    private var processor: Job? = null

    init {
        initialization = scope.async(Dispatchers.IO) { initializeFromDisk() }
        if (autoStart) {
            scope.launch { start() }
        }
    }

    /** Starts queue dispatching. The foreground service is the only production caller. */
    internal suspend fun start() {
        initialization.await()
        processorMutex.withLock {
            downloadQueue.startDispatching()
            if (processor?.isActive != true) {
                processor = scope.launch(Dispatchers.IO) { processQueue() }
            }
        }
    }

    /**
     * Stops the worker without changing a user-requested pause. An active request stays durable
     * and is returned to the front of the in-memory queue when cancellation completes.
     */
    internal suspend fun stop() {
        val activeProcessor = processorMutex.withLock {
            downloadQueue.stopDispatching()
            processor.also { processor = null }
        }
        activeProcessor?.cancelAndJoin()
    }

    suspend fun awaitInitialized() {
        initialization.await()
    }

    /**
     * Enqueues a new capture even when an older completed snapshot exists.
     */
    suspend fun enqueue(request: ThreadDownloadRequest) {
        enqueueInternal(request, skipCompleted = false)
    }

    /**
     * Enqueues a capture only when no readable snapshot or active task exists for the thread.
     *
     * This is intended for bulk actions where an already downloaded thread should be skipped
     * instead of refreshed.
     */
    suspend fun enqueueIfMissing(request: ThreadDownloadRequest): Boolean =
        enqueueInternal(request, skipCompleted = true)

    private suspend fun enqueueInternal(
        request: ThreadDownloadRequest,
        skipCompleted: Boolean,
    ): Boolean {
        initialization.await()
        return withContext(NonCancellable) {
            var entry: ThreadDownloadQueueEntry? = null
            mutationMutex.withLock {
                val active = _statuses.value[request.key]?.phase
                val hasCompleted = _downloads.value.any { it.key == request.key }
                if (
                    (!skipCompleted || !hasCompleted) &&
                    active != ThreadDownloadPhase.QUEUED &&
                    active != ThreadDownloadPhase.FETCHING_PAGES &&
                    active != ThreadDownloadPhase.DOWNLOADING_IMAGES
                ) {
                    val queued = downloadQueue.reserve(request)
                    withContext(Dispatchers.IO) {
                        store.persistRequest(request, queueOrder = queued.order)
                    }
                    cancelledKeys.remove(request.key)
                    publishStatus(
                        request.queuedStatus(
                            existing = _downloads.value.firstOrNull { it.key == request.key },
                        ),
                    )
                    entry = queued
                }
            }
            entry?.let { queued ->
                check(downloadQueue.enqueue(queued)) { "Could not enqueue thread download" }
            }
            entry != null
        }
    }

    suspend fun retry(key: ThreadDownloadKey): Boolean {
        initialization.await()
        return withContext(NonCancellable) {
            var found = false
            var entry: ThreadDownloadQueueEntry? = null
            mutationMutex.withLock {
                val request = withContext(Dispatchers.IO) { store.loadQueuedRequest(key) }
                    ?: _statuses.value[key]?.request
                if (request != null) {
                    found = true
                    val active = _statuses.value[key]?.phase
                    if (
                        active != ThreadDownloadPhase.QUEUED &&
                        active != ThreadDownloadPhase.FETCHING_PAGES &&
                        active != ThreadDownloadPhase.DOWNLOADING_IMAGES
                    ) {
                        val queued = downloadQueue.reserve(request)
                        withContext(Dispatchers.IO) {
                            store.persistRequest(request, queueOrder = queued.order)
                        }
                        cancelledKeys.remove(key)
                        publishStatus(
                            request.queuedStatus(
                                existing = _downloads.value.firstOrNull { it.key == key },
                            ),
                        )
                        entry = queued
                    }
                }
            }
            entry?.let { queued ->
                check(downloadQueue.enqueue(queued)) { "Could not retry thread download" }
            }
            found
        }
    }

    /** Stops the active transfer while retaining it, and every pending task, in the durable queue. */
    suspend fun pauseDownloads(): Boolean {
        initialization.await()
        var activeTask: Job? = null
        val paused = withContext(NonCancellable) {
            mutationMutex.withLock {
                if (downloadQueue.state.value.isPaused) {
                    false
                } else {
                    withContext(Dispatchers.IO) {
                        check(store.setQueuePaused(true)) {
                            "Could not persist the paused download queue"
                        }
                    }
                    activeTask = activeTasks.values.singleOrNull()
                    check(downloadQueue.pause()) { "Could not pause the download queue" }
                    true
                }
            }
        }
        if (paused) {
            withContext(NonCancellable) { activeTask?.cancelAndJoin() }
        }
        return paused
    }

    /** Allows the next pending task to start, or retries the task interrupted by [pauseDownloads]. */
    suspend fun resumeDownloads(): Boolean {
        initialization.await()
        return withContext(NonCancellable) {
            mutationMutex.withLock {
                if (!downloadQueue.state.value.isPaused) {
                    false
                } else {
                    withContext(Dispatchers.IO) {
                        check(store.setQueuePaused(false)) {
                            "Could not persist the resumed download queue"
                        }
                    }
                    check(downloadQueue.resume()) { "Could not resume the download queue" }
                    true
                }
            }
        }
    }

    /** Moves a pending download to the front of the durable FIFO queue. */
    suspend fun prioritize(key: ThreadDownloadKey): Boolean {
        initialization.await()
        return withContext(NonCancellable) {
            mutationMutex.withLock {
                val reordered = downloadQueue.prioritize(key) ?: return@withLock false
                withContext(Dispatchers.IO) {
                    reordered.forEach { entry ->
                        store.persistRequest(entry.request, queueOrder = entry.order)
                    }
                }
                true
            }
        }
    }

    suspend fun read(key: ThreadDownloadKey): DownloadedThread? {
        initialization.await()
        return withContext(Dispatchers.IO) { store.read(key) }
    }

    suspend fun listCompleted(): List<DownloadedThread> {
        initialization.await()
        return withContext(Dispatchers.IO) { store.listCompleted() }
    }

    suspend fun refresh() {
        initialization.await()
        mutationMutex.withLock {
            refreshFromDiskLocked()
        }
    }

    suspend fun delete(key: ThreadDownloadKey) {
        initialization.await()
        withContext(NonCancellable) {
            mutationMutex.withLock {
                cancelledKeys += key
                downloadQueue.cancel(key)
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
                                        error = IOException("Could not remove the thread download"),
                                        existing = null,
                                        progress = previous.progress,
                                    ),
                                )
                            }
                        }
                        throw IOException("Could not remove the thread download")
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
                val cancelled = (previous.keys + downloadQueue.cancelAll()).toSet()
                cancelledKeys += cancelled
                try {
                    val tasks = activeTasks.values.toList()
                    tasks.forEach { it.cancel() }
                    tasks.joinAll()
                    val deleted = withContext(Dispatchers.IO) { store.deleteAll() }
                    if (deleted) {
                        if (downloadQueue.state.value.isPaused) {
                            check(downloadQueue.resume()) {
                                "Could not reset the download queue after deletion"
                            }
                        }
                        _statuses.value = emptyMap()
                        _downloads.value = emptyList()
                    } else {
                        refreshFromDiskLocked()
                        throw IOException("Could not remove all thread downloads")
                    }
                } finally {
                    cancelledKeys.removeAll(cancelled)
                }
            }
        }
    }

    internal fun close() {
        initialization.cancel()
        processor?.cancel()
        downloadQueue.close()
    }

    private suspend fun processQueue() {
        while (true) {
            val entry = downloadQueue.awaitNext()
            val key = entry.request.key
            var activeTask: Job? = null
            try {
                onEntryDequeued(key)
                supervisorScope {
                    mutationMutex.withLock {
                        val queueState = downloadQueue.state.value
                        if (queueState.isRunning && !queueState.isPaused) {
                            val task = launch(start = CoroutineStart.LAZY) { process(key) }
                            activeTask = task
                            activeTasks[key] = task
                            task.start()
                        }
                    }
                    activeTask?.join()
                }
            } finally {
                val task = activeTask
                task?.let { activeTasks.remove(key, it) }
                withContext(NonCancellable) {
                    requeuePausedDownload(
                        key = key,
                        wasCancelled = task?.isCancelled ?: true,
                    )
                }
            }
        }
    }

    private suspend fun initializeFromDisk() {
        val completed: List<DownloadedThread>
        val queued: List<ThreadDownloadQueueEntry>
        val failed: List<ThreadDownloadRequest>
        val paused: Boolean
        withContext(Dispatchers.IO) {
            cleanupLegacyPostDownloads()
            check(store.cleanupStaging()) {
                "Could not remove stale thread download staging files"
            }
            check(store.cleanupQueueArtifacts()) {
                "Could not remove stale thread download queue files"
            }
            completed = store.listCompletedAndCleanupInvalid()
            queued = store.loadQueuedEntries()
            failed = store.loadFailedRequests()
            paused = store.isQueuePaused()
        }
        _downloads.value = completed
        val completeByKey = completed.associateBy(DownloadedThread::key)
        val initialStatuses = completed.associate {
            it.key to it.completedStatus()
        }.toMutableMap()

        failed.forEach { request ->
            val existing = completeByKey[request.key]
            if (existing.isAtLeastAsNewAs(request)) {
                withContext(Dispatchers.IO) { store.removeQueuedRequest(request.key) }
            } else {
                initialStatuses[request.key] = request.failedStatus(
                    error = IOException("Previous thread download failed"),
                    existing = existing,
                )
            }
        }
        val pendingEntries = mutableListOf<ThreadDownloadQueueEntry>()
        queued.forEach { entry ->
            val request = entry.request
            val existing = completeByKey[request.key]
            if (existing.isAtLeastAsNewAs(request)) {
                withContext(Dispatchers.IO) { store.removeQueuedRequest(request.key) }
            } else {
                initialStatuses[request.key] = request.queuedStatus(existing)
                pendingEntries += entry
            }
        }
        _statuses.value = initialStatuses
        downloadQueue.restore(pendingEntries, isPaused = paused)
    }

    private fun cleanupLegacyPostDownloads() {
        val legacy = legacyRootDirectory?.canonicalFile ?: return
        val threadRoot = store.rootDirectory.canonicalFile
        require(
            legacy.name == LEGACY_ROOT_DIRECTORY_NAME &&
                legacy.parentFile == threadRoot.parentFile &&
                legacy != threadRoot,
        ) {
            "Legacy post download cleanup target is invalid"
        }
        if (legacy.exists() && !legacy.deleteRecursively()) {
            AppLogger.warn(TAG) { "Could not remove legacy per-post downloads" }
        }
    }

    private suspend fun process(key: ThreadDownloadKey) {
        if (cancelledKeys.remove(key)) return
        var request: ThreadDownloadRequest? = null
        var staging: ThreadDownloadStaging? = null
        try {
            val activeRequest =
                withContext(Dispatchers.IO) { store.loadQueuedRequest(key) } ?: return
            request = activeRequest
            val existing = withContext(Dispatchers.IO) { store.read(key) }

            publishStatus(activeRequest.fetchingStatus(existing = existing))
            val capture = downloader.download(
                request = activeRequest,
                checkNotCancelled = { checkNotCancelled(key) },
                onPageFetched = { completedPages, totalPages, latestThread ->
                    publishStatus(
                        activeRequest.fetchingStatus(
                            completedPages = completedPages,
                            totalPages = totalPages,
                            thread = latestThread,
                            existing = existing,
                        ),
                    )
                },
                onImageProgress = { snapshot, completedImages, totalImages, downloadedBytes ->
                    publishStatus(
                        activeRequest.downloadingStatus(
                            snapshot = snapshot,
                            completedImages = completedImages,
                            totalImages = totalImages,
                            downloadedBytes = downloadedBytes,
                            existing = existing,
                        ),
                    )
                },
            )
            staging = capture.staging
            checkNotCancelled(key)
            mutationMutex.withLock {
                checkNotCancelled(key)
                withContext(Dispatchers.IO) {
                    store.commit(
                        staging = capture.staging,
                        request = activeRequest,
                        snapshot = capture.snapshot,
                        images = capture.images,
                        completedAt = clock(),
                    )
                    store.removeQueuedRequest(key)
                }
                refreshFromDiskLocked()
            }
        } catch (error: ThreadDownloadCancelledException) {
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
                    "Thread download failed; threadId=${key.threadId}"
                }
                (request ?: _statuses.value[key]?.request)?.let { failedRequest ->
                    val previousStatus = _statuses.value[key]
                    val existing = withContext(Dispatchers.IO) { store.read(key) }
                    try {
                        mutationMutex.withLock {
                            withContext(Dispatchers.IO) {
                                store.persistFailedRequest(failedRequest)
                            }
                            publishStatus(
                                failedRequest.failedStatus(
                                    error = error,
                                    existing = existing,
                                    progress = previousStatus?.progress,
                                ),
                            )
                        }
                    } catch (persistenceError: CancellationException) {
                        throw persistenceError
                    } catch (persistenceError: Exception) {
                        AppLogger.warn(TAG, persistenceError) {
                            "Could not persist failed thread download state; " +
                                "threadId=${key.threadId}"
                        }
                        publishStatus(
                            failedRequest.failedStatus(
                                error = error,
                                existing = existing,
                                progress = previousStatus?.progress,
                            ),
                        )
                    }
                }
            }
        } finally {
            cancelledKeys.remove(key)
        }
    }

    private fun checkNotCancelled(key: ThreadDownloadKey) {
        if (key in cancelledKeys) throw ThreadDownloadCancelledException()
    }

    private suspend fun requeuePausedDownload(
        key: ThreadDownloadKey,
        wasCancelled: Boolean,
    ) {
        val requeued = downloadQueue.finish(key, wasCancelled)
        requeued?.let { pending ->
            publishStatus(
                pending.request.queuedStatus(
                    existing = _downloads.value.firstOrNull { it.key == key },
                ),
            )
        }
    }

    private fun publishStatus(status: ThreadDownloadStatus) {
        _statuses.update { it + (status.key to status) }
    }

    private suspend fun refreshFromDiskLocked() {
        val completed =
            withContext(Dispatchers.IO) { store.listCompletedAndCleanupInvalid() }
        _downloads.value = completed
        val completedByKey = completed.associateBy(DownloadedThread::key)
        _statuses.update { current ->
            buildMap {
                current.values.forEach { status ->
                    val disk = completedByKey[status.key]
                    val request = status.request
                    val pendingNewerCapture =
                        request != null && !disk.isAtLeastAsNewAs(request)
                    if (
                        status.phase != ThreadDownloadPhase.COMPLETED &&
                        (disk == null || pendingNewerCapture)
                    ) {
                        put(status.key, status.copy(completed = disk))
                    }
                }
                completed.forEach { download ->
                    if (download.key !in this) {
                        put(download.key, download.completedStatus())
                    }
                }
            }
        }
    }

    private class ThreadDownloadCancelledException : Exception()

    companion object {
        private const val TAG = "ThreadDownload"
        private const val LEGACY_ROOT_DIRECTORY_NAME = "post-downloads"
    }
}

internal suspend fun fetchCompleteThreadSnapshot(
    source: ThreadPostsSource,
    request: ThreadDownloadRequest,
    onPageFetched: suspend (
        completedPages: Int,
        totalPages: Int,
        latestThread: YamiboThreadDetails,
    ) -> Unit = { _, _, _ -> },
): ThreadDownloadSnapshot {
    val postsById = LinkedHashMap<Int, YamiboPost>()
    var pageNumber = 1
    var capturedPages = 0
    var sourcePageSize: Int? = null
    var sourceTotalPosts = 0
    var latestThread = request.thread
    var poll: YamiboThreadPoll? = null

    while (true) {
        require(pageNumber <= MAX_THREAD_PAGES) {
            "Thread exceeds the maximum supported page count"
        }
        val page = source.getPage(request.key.threadId, pageNumber)
        require(page.thread.id == request.key.threadId) {
            "Thread source returned another thread"
        }
        require(page.pagination.page == pageNumber) {
            "Thread source returned an unexpected page"
        }
        require(page.pagination.pageSize > 0) {
            "Thread source returned an invalid page size"
        }
        val expectedPageSize = sourcePageSize
        if (expectedPageSize == null) {
            sourcePageSize = page.pagination.pageSize
        } else {
            require(page.pagination.pageSize == expectedPageSize) {
                "Thread page size changed during download"
            }
        }
        require(page.posts.all { it.threadId == request.key.threadId }) {
            "Thread source returned a post from another thread"
        }
        val previousPostCount = postsById.size
        page.posts.forEach { post -> postsById[post.id] = post }
        require(
            !page.pagination.hasNextPage ||
                (page.posts.isNotEmpty() && postsById.size > previousPostCount),
        ) {
            "Thread source returned an empty or duplicate-only page before the end"
        }
        require(postsById.size <= MAX_THREAD_POSTS) {
            "Thread exceeds the maximum supported post count"
        }
        latestThread = page.thread
        poll = poll ?: page.poll
        sourceTotalPosts = maxOf(sourceTotalPosts, page.pagination.totalPosts)
        capturedPages++
        val projectedTotal = if (page.pagination.hasNextPage) {
            maxOf(capturedPages + 1, page.pagination.totalPages)
        } else {
            capturedPages
        }
        onPageFetched(capturedPages, projectedTotal, latestThread)
        if (!page.pagination.hasNextPage) break
        pageNumber++
    }

    return ThreadDownloadSnapshot(
        thread = latestThread,
        poll = poll,
        posts = postsById.values.sortedWith(THREAD_POST_READING_ORDER),
        capturedPageCount = capturedPages,
        sourcePageSize = requireNotNull(sourcePageSize),
        sourceTotalPosts = sourceTotalPosts,
    )
}

private const val MAX_THREAD_PAGES = 1_000
private const val MAX_THREAD_POSTS = 100_000

private fun DownloadedThread?.isAtLeastAsNewAs(request: ThreadDownloadRequest): Boolean =
    this != null && manifest.requestedAt >= request.requestedAt

private fun ThreadDownloadRequest.queuedStatus(
    existing: DownloadedThread?,
): ThreadDownloadStatus = ThreadDownloadStatus(
    key = key,
    phase = ThreadDownloadPhase.QUEUED,
    thread = thread,
    progress = ThreadDownloadProgress(0, 0, 0, 0, 0),
    completed = existing,
    request = this,
)

private fun ThreadDownloadRequest.fetchingStatus(
    completedPages: Int = 0,
    totalPages: Int = 0,
    thread: YamiboThreadDetails = this.thread,
    existing: DownloadedThread?,
): ThreadDownloadStatus = ThreadDownloadStatus(
    key = key,
    phase = ThreadDownloadPhase.FETCHING_PAGES,
    thread = thread,
    progress = ThreadDownloadProgress(
        completedPages = completedPages,
        totalPages = totalPages,
        completedImages = 0,
        totalImages = 0,
        downloadedBytes = 0,
    ),
    completed = existing,
    request = this,
)

private fun ThreadDownloadRequest.downloadingStatus(
    snapshot: ThreadDownloadSnapshot,
    completedImages: Int,
    totalImages: Int,
    downloadedBytes: Long,
    existing: DownloadedThread?,
): ThreadDownloadStatus = ThreadDownloadStatus(
    key = key,
    phase = ThreadDownloadPhase.DOWNLOADING_IMAGES,
    thread = snapshot.thread,
    progress = ThreadDownloadProgress(
        completedPages = snapshot.capturedPageCount,
        totalPages = snapshot.capturedPageCount,
        completedImages = completedImages,
        totalImages = totalImages,
        downloadedBytes = downloadedBytes,
    ),
    completed = existing,
    request = this,
)

private fun ThreadDownloadRequest.failedStatus(
    error: Exception,
    existing: DownloadedThread?,
    progress: ThreadDownloadProgress? = null,
): ThreadDownloadStatus = ThreadDownloadStatus(
    key = key,
    phase = ThreadDownloadPhase.FAILED,
    thread = thread,
    progress = progress ?: ThreadDownloadProgress(0, 0, 0, 0, 0),
    error = error.message ?: "Thread download failed",
    completed = existing,
    request = this,
)

private fun DownloadedThread.completedStatus(): ThreadDownloadStatus =
    ThreadDownloadStatus(
        key = key,
        phase = ThreadDownloadPhase.COMPLETED,
        thread = snapshot.thread,
        progress = ThreadDownloadProgress(
            completedPages = snapshot.capturedPageCount,
            totalPages = snapshot.capturedPageCount,
            completedImages = manifest.images.size,
            totalImages = manifest.images.size,
            downloadedBytes = manifest.images.sumOf(ThreadDownloadImage::byteCount),
        ),
        completed = this,
    )
