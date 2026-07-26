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

class PostDownloadRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun textOnlyPostCompletesWithoutMakingAnImageRequest() = runBlocking {
        val store = PostDownloadFileStore(temporaryFolder.newFolder("downloads"))
        val downloader = FakeImageDownloader()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = PostDownloadRepository(store, downloader, scope) { 20L }
        try {
            val request = testDownloadRequest()
            repository.enqueue(request)

            val completed = repository.awaitPhase(request.key, PostDownloadPhase.COMPLETED)

            assertEquals(0, downloader.callCount)
            assertNotNull(completed.completed)
            assertEquals(listOf(request.key), repository.downloads.value.map { it.key })
            assertNull(store.loadQueuedRequest(request.key))
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun imageFailureLeavesNoProductAndRetryCompletesFromPersistedRequest() = runBlocking {
        val store = PostDownloadFileStore(temporaryFolder.newFolder("downloads"))
        val downloader = FakeImageDownloader(failOnceAtCall = 2)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = PostDownloadRepository(store, downloader, scope) { 20L }
        try {
            val request = testDownloadRequest(
                html = "<p>Text</p><img><img>",
                imageUrls = listOf(
                    "https://bbs.yamibo.com/one.png",
                    "https://bbs.yamibo.com/two.png",
                ),
            )
            repository.enqueue(request)

            repository.awaitPhase(request.key, PostDownloadPhase.FAILED)
            assertNull(store.read(request.key))
            assertNull(store.loadQueuedRequest(request.key))
            assertEquals(listOf(request), store.loadFailedRequests())
            assertTrue(repository.retry(request.key))

            val completed = repository.awaitPhase(request.key, PostDownloadPhase.COMPLETED)

            assertNotNull(completed.completed)
            assertEquals(4, downloader.callCount)
            assertEquals(2, completed.progress.completedImages)
            assertEquals(request.remoteImageUrls, completed.completed!!.localImageUris.keys.toList())
            assertNull(store.loadQueuedRequest(request.key))
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun repositoryRestartResumesRequestPersistedBeforeProcessDeath() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val request = testDownloadRequest()
        PostDownloadFileStore(root).persistRequest(request)
        val downloader = FakeImageDownloader()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = PostDownloadRepository(
            PostDownloadFileStore(root),
            downloader,
            scope,
        ) { 20L }
        try {
            val completed = repository.awaitPhase(request.key, PostDownloadPhase.COMPLETED)

            assertNotNull(completed.completed)
            assertEquals(0, downloader.callCount)
            assertNull(PostDownloadFileStore(root).loadQueuedRequest(request.key))
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun explicitFailureDoesNotRetryAfterRepositoryRestart() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val request = testDownloadRequest(
            html = "<img>",
            imageUrls = listOf("https://bbs.yamibo.com/one.png"),
            hasText = false,
        )
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstRepository = PostDownloadRepository(
            PostDownloadFileStore(root),
            FakeImageDownloader(failOnceAtCall = 1),
            firstScope,
        ) { 20L }
        try {
            firstRepository.enqueue(request)
            firstRepository.awaitPhase(request.key, PostDownloadPhase.FAILED)
            assertNull(PostDownloadFileStore(root).loadQueuedRequest(request.key))
        } finally {
            firstRepository.close()
            firstScope.cancel()
        }

        val restartDownloader = FakeImageDownloader()
        val restartScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val restartedRepository = PostDownloadRepository(
            PostDownloadFileStore(root),
            restartDownloader,
            restartScope,
        ) { 30L }
        try {
            restartedRepository.awaitInitialized()

            assertEquals(
                PostDownloadPhase.FAILED,
                restartedRepository.statuses.value[request.key]?.phase,
            )
            assertEquals(0, restartDownloader.callCount)
            assertTrue(restartedRepository.retry(request.key))
            restartedRepository.awaitPhase(request.key, PostDownloadPhase.COMPLETED)
            assertEquals(1, restartDownloader.callCount)
        } finally {
            restartedRepository.close()
            restartScope.cancel()
        }
    }

    @Test
    fun validFinalManifestWinsOverLeftoverQueueAfterRestart() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val request = testDownloadRequest(
            html = "<img>",
            imageUrls = listOf("https://bbs.yamibo.com/one.png"),
            hasText = false,
        )
        val staging = store.createStaging(request.key)
        store.commit(
            staging,
            request,
            listOf(stagedImage(staging, 0, request.remoteImageUrls.single())),
            completedAt = 20L,
        )
        store.persistRequest(request)
        val downloader = FakeImageDownloader()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = PostDownloadRepository(
            PostDownloadFileStore(root),
            downloader,
            scope,
        ) { 30L }
        try {
            repository.awaitInitialized()
            val status = repository.statuses.value[request.key]

            assertEquals(PostDownloadPhase.COMPLETED, status?.phase)
            assertEquals(0, downloader.callCount)
            assertNull(PostDownloadFileStore(root).loadQueuedRequest(request.key))
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun stagingFailureDoesNotStopTheNextQueuedDownload() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val store = PostDownloadFileStore(root)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = PostDownloadRepository(store, FakeImageDownloader(), scope) { 20L }
        try {
            repository.awaitInitialized()
            val stagingRoot = File(root, ".staging")
            assertTrue(stagingRoot.deleteRecursively())
            stagingRoot.writeText("blocks child directories")

            val failed = testDownloadRequest(threadId = 1000, postId = 2000)
            repository.enqueue(failed)
            repository.awaitPhase(failed.key, PostDownloadPhase.FAILED)

            assertTrue(stagingRoot.delete())
            assertTrue(stagingRoot.mkdir())
            val next = testDownloadRequest(threadId = 1001, postId = 2001)
            repository.enqueue(next)

            assertNotNull(
                repository.awaitPhase(next.key, PostDownloadPhase.COMPLETED).completed,
            )
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun deletingAnActiveDownloadCancelsItsImageRequest() = runBlocking {
        val root = temporaryFolder.newFolder("downloads")
        val downloader = CancellableImageDownloader()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = PostDownloadRepository(
            PostDownloadFileStore(root),
            downloader,
            scope,
        ) { 20L }
        try {
            val request = testDownloadRequest(
                html = "<img>",
                imageUrls = listOf("https://bbs.yamibo.com/slow.png"),
                hasText = false,
            )
            repository.enqueue(request)
            withTimeout(5_000) { downloader.started.await() }

            repository.delete(request.key)

            withTimeout(5_000) { downloader.cancelled.await() }
            assertNull(repository.statuses.value[request.key])
            assertNull(PostDownloadFileStore(root).loadQueuedRequest(request.key))
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    @Test
    fun deleteAndDeleteAllRefreshObservableCompletedDownloads() = runBlocking {
        val store = PostDownloadFileStore(temporaryFolder.newFolder("downloads"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = PostDownloadRepository(store, FakeImageDownloader(), scope) { 20L }
        try {
            val first = testDownloadRequest(threadId = 1000, postId = 2000)
            val second = testDownloadRequest(threadId = 1001, postId = 2001)
            repository.enqueue(first)
            repository.enqueue(second)
            repository.awaitPhase(first.key, PostDownloadPhase.COMPLETED)
            repository.awaitPhase(second.key, PostDownloadPhase.COMPLETED)

            repository.delete(first.key)
            assertFalse(repository.downloads.value.any { it.key == first.key })
            assertTrue(repository.downloads.value.any { it.key == second.key })

            repository.deleteAll()
            assertTrue(repository.downloads.value.isEmpty())
            assertTrue(repository.statuses.value.isEmpty())
        } finally {
            repository.close()
            scope.cancel()
        }
    }

    private suspend fun PostDownloadRepository.awaitPhase(
        key: PostDownloadKey,
        phase: PostDownloadPhase,
    ): PostDownloadStatus = withTimeout(5_000) {
        statuses.map { it[key] }.first { it?.phase == phase }!!
    }

    private class FakeImageDownloader(
        private val failOnceAtCall: Int? = null,
    ) : PostImageDownloader {
        var callCount = 0
            private set
        private var failed = false

        override suspend fun download(
            request: PostImageDownloadRequest,
            destination: File,
        ): PostImageDownloadResult {
            callCount++
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
