package com.yamibo.pocket300.data.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ThreadDownloadRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadsEveryPageBeforeCompletingTextOnlyThread() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val thread = testThread(replyCount = 2)
        val calls = mutableListOf<Int>()
        val source = ThreadPostsSource { _, page ->
            calls += page
            when (page) {
                1 -> testPage(
                    thread,
                    page = 1,
                    totalPages = 2,
                    posts = listOf(
                        testPost(thread.id, 2000, 1),
                        testPost(thread.id, 2001, 2),
                    ),
                )
                2 -> testPage(
                    thread,
                    page = 2,
                    totalPages = 2,
                    posts = listOf(testPost(thread.id, 2002, 3)),
                )
                else -> error("unexpected page")
            }
        }
        val downloader = FakeImageDownloader()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, downloader, scope)
        try {
            val request = testRequest(thread)
            repository.enqueue(request)

            val status = repository.awaitCompleted(request.key, request.requestedAt)

            assertEquals(listOf(1, 2), calls)
            assertEquals(3, status.completed!!.snapshot.posts.size)
            assertEquals(2, status.progress.completedPages)
            assertEquals(0, downloader.requests.size)
            assertNull(ThreadDownloadFileStore(root).loadQueuedRequest(request.key))
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun aggregatesAndDownloadsDistinctImagesFromAllPosts() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val thread = testThread()
        val first = "https://bbs.yamibo.com/first.png"
        val second = "https://bbs.yamibo.com/second.png"
        val source = ThreadPostsSource { _, _ ->
            testPage(
                thread,
                page = 1,
                totalPages = 1,
                posts = listOf(
                    testPost(
                        thread.id,
                        2000,
                        1,
                        html = "<img data-src='$first'><img src='$first'>",
                    ),
                    testPost(
                        thread.id,
                        2001,
                        2,
                        attachmentUrls = listOf(first, second),
                    ),
                ),
            )
        }
        val downloader = FakeImageDownloader()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, downloader, scope)
        try {
            val request = testRequest(thread)
            repository.enqueue(request)

            val completed = repository.awaitCompleted(request.key, request.requestedAt).completed!!

            assertEquals(listOf(first, second), downloader.requests)
            assertEquals(listOf(first, second), completed.localImageUris.keys.toList())
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun failedImageRequestIsDurableAndManualRetryCompletes() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val thread = testThread(replyCount = 0)
        val image = "https://bbs.yamibo.com/image.png"
        val source = singlePageSource(
            thread,
            listOf(testPost(thread.id, 2000, 1, "<img src='$image'>")),
        )
        val downloader = FakeImageDownloader(failOnceAtCall = 1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, downloader, scope)
        try {
            val request = testRequest(thread)
            repository.enqueue(request)
            repository.awaitPhase(request.key, ThreadDownloadPhase.FAILED)

            assertNull(ThreadDownloadFileStore(root).loadQueuedRequest(request.key))
            assertEquals(listOf(request), ThreadDownloadFileStore(root).loadFailedRequests())
            assertTrue(repository.retry(request.key))

            assertNotNull(repository.awaitCompleted(request.key, request.requestedAt).completed)
            assertEquals(2, downloader.callCount)
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun completedThreadCanBeRedownloadedAndAtomicallyUpdated() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        var currentThread = testThread(subject = "First")
        val source = ThreadPostsSource { _, _ ->
            testPage(
                currentThread,
                page = 1,
                totalPages = 1,
                posts = listOf(testPost(currentThread.id, 2000, 1)),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, FakeImageDownloader(), scope)
        try {
            val firstRequest = testRequest(currentThread, requestedAt = 10L)
            repository.enqueue(firstRequest)
            val first = repository.awaitCompleted(firstRequest.key, 10L).completed!!

            currentThread = currentThread.copy(subject = "Second")
            val secondRequest = testRequest(currentThread, requestedAt = 20L)
            repository.enqueue(secondRequest)
            val second = repository.awaitCompleted(secondRequest.key, 20L).completed!!

            assertEquals("Second", second.snapshot.thread.subject)
            assertTrue(second.directory.exists())
            assertTrue(!first.directory.exists())
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun enqueueIfMissingSkipsCompletedThreadInsteadOfRefreshingIt() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        var sourceCalls = 0
        val firstThread = testThread(subject = "First")
        val source = ThreadPostsSource { _, _ ->
            sourceCalls++
            testPage(
                firstThread,
                page = 1,
                totalPages = 1,
                posts = listOf(testPost(firstThread.id, 2000, 1)),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, FakeImageDownloader(), scope)
        try {
            val firstRequest = testRequest(firstThread, requestedAt = 10L)
            assertTrue(repository.enqueueIfMissing(firstRequest))
            repository.awaitCompleted(firstRequest.key, 10L)

            val refresh = testRequest(firstThread.copy(subject = "Refresh"), requestedAt = 20L)
            assertFalse(repository.enqueueIfMissing(refresh))

            assertEquals(1, sourceCalls)
            assertEquals("First", repository.read(firstRequest.key)?.snapshot?.thread?.subject)
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun enqueueIfMissingSkipsAnActiveThreadWithoutReplacingItsRequest() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var sourceCalls = 0
        val thread = testThread(subject = "First")
        val source = ThreadPostsSource { _, _ ->
            sourceCalls++
            started.complete(Unit)
            release.await()
            testPage(
                thread,
                page = 1,
                totalPages = 1,
                posts = listOf(testPost(thread.id, 2000, 1)),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, FakeImageDownloader(), scope)
        try {
            val firstRequest = testRequest(thread, requestedAt = 10L)
            assertTrue(repository.enqueueIfMissing(firstRequest))
            withTimeout(5_000) { started.await() }

            val duplicate = testRequest(thread.copy(subject = "Duplicate"), requestedAt = 20L)
            assertFalse(repository.enqueueIfMissing(duplicate))
            release.complete(Unit)

            val completed = repository.awaitCompleted(firstRequest.key, 10L)
            assertEquals(1, sourceCalls)
            assertEquals("First", completed.completed?.snapshot?.thread?.subject)
        } finally {
            release.complete(Unit)
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun crossThreadSourceFailsWithoutReplacingExistingProduct() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val thread = testThread()
        val store = ThreadDownloadFileStore(root)
        val oldRequest = testRequest(thread, requestedAt = 10L)
        store.commit(
            store.createStaging(oldRequest.key),
            oldRequest,
            testSnapshot(thread = thread),
            emptyList(),
            completedAt = 11L,
        )
        val source = ThreadPostsSource { _, _ ->
            val wrong = testThread(threadId = 9999)
            testPage(
                wrong,
                page = 1,
                totalPages = 1,
                posts = listOf(testPost(wrong.id, 9000, 1)),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, FakeImageDownloader(), scope)
        try {
            val refresh = testRequest(thread.copy(subject = "Refresh"), requestedAt = 20L)
            repository.enqueue(refresh)

            val failed = repository.awaitPhase(refresh.key, ThreadDownloadPhase.FAILED)

            assertEquals("Offline subject", failed.completed?.snapshot?.thread?.subject)
            assertEquals("Offline subject", repository.read(refresh.key)?.snapshot?.thread?.subject)
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun pendingRequestAndLegacyCleanupAreHandledDuringIoInitialization() = runBlocking {
        val parent = temporaryFolder.newFolder("no-backup")
        val root = File(parent, "thread-downloads")
        val legacy = File(parent, "post-downloads").apply {
            resolve("1000/2000/images").mkdirs()
            resolve("1000/2000/images/old.img").writeBytes(TEST_PNG_BYTES)
        }
        val request = testRequest()
        ThreadDownloadFileStore(root).persistRequest(request)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = ThreadDownloadRepository(
            store = ThreadDownloadFileStore(root),
            threadPostsSource = singlePageSource(
                request.thread,
                listOf(
                    testPost(request.thread.id, 2000, 1),
                    testPost(request.thread.id, 2001, 2),
                ),
            ),
            imageDownloader = FakeImageDownloader(),
            scope = scope,
            clock = { 20L },
            legacyRootDirectory = legacy,
        )
        try {
            repository.awaitCompleted(request.key, request.requestedAt)

            assertTrue(!legacy.exists())
            assertNotNull(repository.read(request.key))
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun restoresPausedQueueWithoutStartingWorkerUntilResumed() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val request = testRequest()
        val store = ThreadDownloadFileStore(root)
        store.persistRequest(request)
        assertTrue(store.setQueuePaused(true))
        var sourceCalls = 0
        val source = ThreadPostsSource { _, _ ->
            sourceCalls++
            testPage(
                thread = request.thread,
                page = 1,
                totalPages = 1,
                posts = listOf(testPost(request.thread.id, 2000, 1)),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, FakeImageDownloader(), scope)
        try {
            repository.awaitInitialized()

            assertTrue(repository.queueState.value.isPaused)
            assertEquals(listOf(request.key), repository.queueState.value.queuedKeys)
            assertEquals(0, sourceCalls)

            assertTrue(repository.resumeDownloads())
            repository.awaitCompleted(request.key, request.requestedAt)
            assertEquals(1, sourceCalls)
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun failedThreadDoesNotBlockTheNextQueuedThread() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val failedThread = testThread(threadId = 1000, replyCount = 0)
        val nextThread = testThread(threadId = 1001, replyCount = 0)
        val image = "https://bbs.yamibo.com/fail.png"
        val source = ThreadPostsSource { threadId, _ ->
            when (threadId) {
                failedThread.id -> testPage(
                    failedThread,
                    page = 1,
                    totalPages = 1,
                    posts = listOf(
                        testPost(failedThread.id, 2000, 1, "<img src='$image'>"),
                    ),
                )
                nextThread.id -> testPage(
                    nextThread,
                    page = 1,
                    totalPages = 1,
                    posts = listOf(testPost(nextThread.id, 3000, 1)),
                )
                else -> error("unexpected thread")
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(
            root,
            source,
            FakeImageDownloader(failOnceAtCall = 1),
            scope,
        )
        try {
            val failedRequest = testRequest(failedThread, requestedAt = 10L)
            val nextRequest = testRequest(nextThread, requestedAt = 11L)
            repository.enqueue(failedRequest)
            repository.enqueue(nextRequest)

            repository.awaitPhase(failedRequest.key, ThreadDownloadPhase.FAILED)
            val completed = repository.awaitCompleted(nextRequest.key, nextRequest.requestedAt)

            assertNotNull(completed.completed)
            assertNull(repository.read(failedRequest.key))
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun pausingActiveDownloadRequeuesItBeforeRemainingTasks() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val first = testThread(threadId = 1000, replyCount = 0)
        val second = testThread(threadId = 1001, replyCount = 0)
        val firstStarted = CompletableDeferred<Unit>()
        val allowRetry = CompletableDeferred<Unit>()
        var firstCalls = 0
        val source = ThreadPostsSource { threadId, _ ->
            when (threadId) {
                first.id -> {
                    firstCalls++
                    if (firstCalls == 1) {
                        firstStarted.complete(Unit)
                        allowRetry.await()
                    }
                    testPage(
                        first,
                        page = 1,
                        totalPages = 1,
                        posts = listOf(testPost(first.id, 2000, 1)),
                    )
                }

                second.id -> testPage(
                    second,
                    page = 1,
                    totalPages = 1,
                    posts = listOf(testPost(second.id, 3000, 1)),
                )

                else -> error("unexpected thread")
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(root, source, FakeImageDownloader(), scope)
        val firstRequest = testRequest(first, requestedAt = 10L)
        val secondRequest = testRequest(second, requestedAt = 11L)
        try {
            repository.enqueue(firstRequest)
            repository.enqueue(secondRequest)
            withTimeout(5_000) { firstStarted.await() }

            assertTrue(repository.pauseDownloads())
            val paused = withTimeout(5_000) {
                repository.queueState.first {
                    it.isPaused &&
                        it.activeKey == null &&
                        it.queuedKeys == listOf(firstRequest.key, secondRequest.key)
                }
            }
            assertEquals(listOf(firstRequest.key, secondRequest.key), paused.queuedKeys)
            assertEquals(
                listOf(firstRequest, secondRequest),
                ThreadDownloadFileStore(root).loadQueuedRequests(),
            )

            allowRetry.complete(Unit)
            assertTrue(repository.resumeDownloads())
            repository.awaitCompleted(firstRequest.key, firstRequest.requestedAt)
            repository.awaitCompleted(secondRequest.key, secondRequest.requestedAt)
            assertEquals(2, firstCalls)
        } finally {
            allowRetry.complete(Unit)
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun stoppingWorkerRequeuesActiveDownloadWithoutPausingIt() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val thread = testThread(replyCount = 0)
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val source = ThreadPostsSource { _, _ ->
            calls++
            if (calls == 1) {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
            testPage(
                thread = thread,
                page = 1,
                totalPages = 1,
                posts = listOf(testPost(thread.id, 2000, 1)),
            )
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(
            root = root,
            source = source,
            downloader = FakeImageDownloader(),
            scope = scope,
            autoStart = false,
        )
        try {
            val request = testRequest(thread)
            repository.enqueue(request)
            repository.start()
            withTimeout(5_000) { started.await() }

            repository.stop()

            assertFalse(repository.queueState.value.isRunning)
            assertFalse(repository.queueState.value.isPaused)
            assertEquals(listOf(request.key), repository.queueState.value.queuedKeys)

            repository.start()
            repository.awaitCompleted(request.key, request.requestedAt)
            assertEquals(2, calls)
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun stoppingWorkerAfterQueueDequeueReturnsEntryForNextServiceGeneration() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val thread = testThread(replyCount = 0)
        val dequeued = CompletableDeferred<Unit>()
        val releaseDequeuedWorker = CompletableDeferred<Unit>()
        var dequeues = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = ThreadDownloadRepository(
            store = ThreadDownloadFileStore(root),
            threadPostsSource = ThreadPostsSource { _, _ ->
                testPage(
                    thread = thread,
                    page = 1,
                    totalPages = 1,
                    posts = listOf(testPost(thread.id, 2000, 1)),
                )
            },
            imageDownloader = FakeImageDownloader(),
            scope = scope,
            autoStart = false,
            onEntryDequeued = {
                dequeues++
                if (dequeues == 1) {
                    dequeued.complete(Unit)
                    releaseDequeuedWorker.await()
                }
            },
        )
        try {
            val request = testRequest(thread)
            repository.enqueue(request)
            repository.start()
            withTimeout(5_000) { dequeued.await() }

            repository.stop()

            assertFalse(repository.queueState.value.isRunning)
            assertFalse(repository.queueState.value.isPaused)
            assertNull(repository.queueState.value.activeKey)
            assertEquals(listOf(request.key), repository.queueState.value.queuedKeys)

            repository.start()
            repository.awaitCompleted(request.key, request.requestedAt)
            assertEquals(2, dequeues)
        } finally {
            releaseDequeuedWorker.complete(Unit)
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun prioritizingPendingDownloadPersistsNewFifoOrder() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(
            root,
            ThreadPostsSource { _, _ -> error("Downloads must remain paused") },
            FakeImageDownloader(),
            scope,
        )
        val first = testRequest(testThread(threadId = 1000), requestedAt = 10L)
        val second = testRequest(testThread(threadId = 1001), requestedAt = 11L)
        val third = testRequest(testThread(threadId = 1002), requestedAt = 12L)
        try {
            assertTrue(repository.pauseDownloads())
            repository.enqueue(first)
            repository.enqueue(second)
            repository.enqueue(third)
            withTimeout(5_000) {
                repository.queueState.first {
                    it.queuedKeys == listOf(first.key, second.key, third.key)
                }
            }

            assertTrue(repository.prioritize(third.key))

            assertEquals(
                listOf(third.key, first.key, second.key),
                repository.queueState.value.queuedKeys,
            )
            assertEquals(
                listOf(third, first, second),
                ThreadDownloadFileStore(root).loadQueuedRequests(),
            )
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun deletingActiveThreadCancelsTransportAndCleansEveryProduct() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val thread = testThread(replyCount = 0)
        val image = "https://bbs.yamibo.com/slow.png"
        val downloader = CancellableImageDownloader()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = repository(
            root,
            singlePageSource(
                thread,
                listOf(testPost(thread.id, 2000, 1, "<img src='$image'>")),
            ),
            downloader,
            scope,
        )
        try {
            val request = testRequest(thread)
            repository.enqueue(request)
            withTimeout(5_000) { downloader.started.await() }

            repository.delete(request.key)

            withTimeout(5_000) { downloader.cancelled.await() }
            assertNull(repository.statuses.value[request.key])
            assertNull(repository.read(request.key))
            assertNull(ThreadDownloadFileStore(root).loadQueuedRequest(request.key))
            assertTrue(root.resolve(".staging").list().orEmpty().isEmpty())
            assertFalse(root.resolve(thread.id.toString()).exists())
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    private fun repository(
        root: File,
        source: ThreadPostsSource,
        downloader: PostImageDownloader,
        scope: CoroutineScope,
        autoStart: Boolean = true,
    ) = ThreadDownloadRepository(
        store = ThreadDownloadFileStore(root),
        threadPostsSource = source,
        imageDownloader = downloader,
        scope = scope,
        clock = { 30L },
        autoStart = autoStart,
    )

    private fun singlePageSource(
        thread: com.yamibo.pocket300.api.YamiboThreadDetails,
        posts: List<com.yamibo.pocket300.api.YamiboPost>,
    ) = ThreadPostsSource { _, page ->
        require(page == 1)
        testPage(
            thread = thread,
            page = 1,
            totalPages = 1,
            posts = posts,
            pageSize = 20,
            totalPosts = posts.size,
        )
    }

    private suspend fun ThreadDownloadRepository.awaitPhase(
        key: ThreadDownloadKey,
        phase: ThreadDownloadPhase,
    ): ThreadDownloadStatus = withTimeout(5_000) {
        statuses.map { it[key] }.first { it?.phase == phase }!!
    }

    private suspend fun ThreadDownloadRepository.awaitCompleted(
        key: ThreadDownloadKey,
        requestedAt: Long,
    ): ThreadDownloadStatus = withTimeout(5_000) {
        statuses.map { it[key] }.first {
            it?.phase == ThreadDownloadPhase.COMPLETED &&
                it.completed?.manifest?.requestedAt == requestedAt
        }!!
    }

    private class FakeImageDownloader(
        private val failOnceAtCall: Int? = null,
    ) : PostImageDownloader {
        val requests = mutableListOf<String>()
        var callCount = 0
            private set
        private var failed = false

        override suspend fun download(
            request: PostImageDownloadRequest,
            destination: File,
        ): PostImageDownloadResult {
            callCount++
            requests += request.remoteUrl
            if (!failed && callCount == failOnceAtCall) {
                failed = true
                throw IOException("simulated image failure")
            }
            destination.writeBytes(TEST_PNG_BYTES)
            return PostImageDownloadResult(
                byteCount = destination.length(),
                contentType = "image/png",
            )
        }
    }

    private class CancellableImageDownloader : PostImageDownloader {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        override suspend fun download(
            request: PostImageDownloadRequest,
            destination: File,
        ): PostImageDownloadResult = suspendCancellableCoroutine { continuation ->
            started.complete(Unit)
            continuation.invokeOnCancellation {
                cancelled.complete(Unit)
            }
        }
    }
}
