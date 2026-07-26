package com.yamibo.pocket300.data.download

import android.content.Context
import com.yamibo.pocket300.Pocket300Application
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
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
 * Application-level single-consumer queue for complete thread downloads.
 *
 * Queue records are durable before work is offered to the channel. A refresh builds and validates
 * a new immutable version while the prior completed version remains readable.
 */
class ThreadDownloadRepository internal constructor(
    private val store: ThreadDownloadFileStore,
    private val threadPostsSource: ThreadPostsSource,
    private val imageDownloader: PostImageDownloader,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val legacyRootDirectory: File? = null,
) {
    private val queue = Channel<ThreadDownloadKey>(Channel.UNLIMITED)
    private val cancelledKeys = ConcurrentHashMap.newKeySet<ThreadDownloadKey>()
    private val activeTasks = ConcurrentHashMap<ThreadDownloadKey, Job>()
    private val mutationMutex = Mutex()
    private val _downloads = MutableStateFlow<List<DownloadedThread>>(emptyList())
    private val _statuses =
        MutableStateFlow<Map<ThreadDownloadKey, ThreadDownloadStatus>>(emptyMap())

    val downloads: StateFlow<List<DownloadedThread>> = _downloads.asStateFlow()
    val statuses: StateFlow<Map<ThreadDownloadKey, ThreadDownloadStatus>> =
        _statuses.asStateFlow()

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

    /**
     * Enqueues a new capture even when an older completed snapshot exists.
     */
    suspend fun enqueue(request: ThreadDownloadRequest) {
        initialization.await()
        var shouldQueue = false
        mutationMutex.withLock {
            val active = _statuses.value[request.key]?.phase
            if (
                active != ThreadDownloadPhase.QUEUED &&
                active != ThreadDownloadPhase.FETCHING_PAGES &&
                active != ThreadDownloadPhase.DOWNLOADING_IMAGES
            ) {
                withContext(Dispatchers.IO) { store.persistRequest(request) }
                cancelledKeys.remove(request.key)
                publishStatus(
                    request.queuedStatus(
                        existing = _downloads.value.firstOrNull { it.key == request.key },
                    ),
                )
                shouldQueue = true
            }
        }
        if (shouldQueue) queue.send(request.key)
    }

    suspend fun retry(key: ThreadDownloadKey): Boolean {
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
                    active != ThreadDownloadPhase.QUEUED &&
                    active != ThreadDownloadPhase.FETCHING_PAGES &&
                    active != ThreadDownloadPhase.DOWNLOADING_IMAGES
                ) {
                    withContext(Dispatchers.IO) { store.persistRequest(request) }
                    cancelledKeys.remove(key)
                    publishStatus(
                        request.queuedStatus(
                            existing = _downloads.value.firstOrNull { it.key == key },
                        ),
                    )
                    shouldQueue = true
                }
            }
        }
        if (shouldQueue) queue.send(key)
        return found
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
                cancelledKeys += previous.keys
                try {
                    val tasks = activeTasks.values.toList()
                    tasks.forEach { it.cancel() }
                    tasks.joinAll()
                    val deleted = withContext(Dispatchers.IO) { store.deleteAll() }
                    if (deleted) {
                        _statuses.value = emptyMap()
                        _downloads.value = emptyList()
                    } else {
                        refreshFromDiskLocked()
                        throw IOException("Could not remove all thread downloads")
                    }
                } finally {
                    cancelledKeys.removeAll(previous.keys)
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
        val completed: List<DownloadedThread>
        val queued: List<ThreadDownloadRequest>
        val failed: List<ThreadDownloadRequest>
        withContext(Dispatchers.IO) {
            cleanupLegacyPostDownloads()
            check(store.cleanupStaging()) {
                "Could not remove stale thread download staging files"
            }
            check(store.cleanupQueueArtifacts()) {
                "Could not remove stale thread download queue files"
            }
            completed = store.listCompletedAndCleanupInvalid()
            queued = store.loadQueuedRequests()
            failed = store.loadFailedRequests()
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
        queued.forEach { request ->
            val existing = completeByKey[request.key]
            if (existing.isAtLeastAsNewAs(request)) {
                withContext(Dispatchers.IO) { store.removeQueuedRequest(request.key) }
            } else {
                initialStatuses[request.key] = request.queuedStatus(existing)
                queue.send(request.key)
            }
        }
        _statuses.value = initialStatuses
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
            val snapshot = fetchCompleteThreadSnapshot(
                source = threadPostsSource,
                request = activeRequest,
            ) { completedPages, totalPages, latestThread ->
                publishStatus(
                    activeRequest.fetchingStatus(
                        completedPages = completedPages,
                        totalPages = totalPages,
                        thread = latestThread,
                        existing = existing,
                    ),
                )
            }
            checkNotCancelled(key)

            val remoteImageUrls = threadImageUrls(snapshot.posts)
            require(remoteImageUrls.size <= MAX_THREAD_IMAGES) {
                "Thread contains too many downloadable images"
            }
            val activeStaging = withContext(Dispatchers.IO) { store.createStaging(key) }
            staging = activeStaging
            val images = ArrayList<ThreadDownloadImage>(remoteImageUrls.size)
            var downloadedBytes = 0L
            publishStatus(
                activeRequest.downloadingStatus(
                    snapshot = snapshot,
                    completedImages = 0,
                    totalImages = remoteImageUrls.size,
                    downloadedBytes = 0,
                    existing = existing,
                ),
            )
            remoteImageUrls.forEachIndexed { index, remoteUrl ->
                checkNotCancelled(key)
                val imageFile = activeStaging.imageFile(index)
                val referer = snapshot.thread.webUrl.takeIf(String::isNotBlank)
                    ?: activeRequest.referer
                val result = imageDownloader.download(
                    PostImageDownloadRequest(remoteUrl, referer),
                    imageFile,
                )
                check(imageFile.isFile && imageFile.length() == result.byteCount) {
                    "Downloaded image size did not match the response"
                }
                downloadedBytes += result.byteCount
                images += ThreadDownloadImage(
                    remoteUrl = remoteUrl,
                    relativePath = "${ThreadDownloadStaging.IMAGE_DIRECTORY_NAME}/${imageFile.name}",
                    byteCount = result.byteCount,
                    sha256 = withContext(Dispatchers.IO) { fileSha256(imageFile) },
                    contentType = result.contentType,
                )
                publishStatus(
                    activeRequest.downloadingStatus(
                        snapshot = snapshot,
                        completedImages = index + 1,
                        totalImages = remoteImageUrls.size,
                        downloadedBytes = downloadedBytes,
                        existing = existing,
                    ),
                )
            }
            checkNotCancelled(key)
            mutationMutex.withLock {
                checkNotCancelled(key)
                withContext(Dispatchers.IO) {
                    store.commit(
                        staging = activeStaging,
                        request = activeRequest,
                        snapshot = snapshot,
                        images = images,
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
        private const val ROOT_DIRECTORY_NAME = "thread-downloads"
        private const val LEGACY_ROOT_DIRECTORY_NAME = "post-downloads"
        private const val MAX_THREAD_IMAGES = 10_000

        @Volatile
        private var instance: ThreadDownloadRepository? = null

        fun getInstance(context: Context): ThreadDownloadRepository =
            instance ?: synchronized(this) {
                instance ?: createApplicationRepository(context.applicationContext)
                    .also { instance = it }
            }

        private fun createApplicationRepository(context: Context): ThreadDownloadRepository {
            val noBackupRoot = context.noBackupFilesDir
            val root = File(noBackupRoot, ROOT_DIRECTORY_NAME)
            return ThreadDownloadRepository(
                store = ThreadDownloadFileStore(
                    rootDirectory = root,
                    decoderValidator = AndroidThreadDownloadImageDecoderValidator,
                ),
                threadPostsSource = YamiboThreadPostsSource(Pocket300Application.api.posts),
                imageDownloader = OkHttpPostImageDownloader(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                legacyRootDirectory = File(noBackupRoot, LEGACY_ROOT_DIRECTORY_NAME),
            )
        }
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
