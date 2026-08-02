package com.yamibo.pocket300.data.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThreadDownloadManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun enqueuePersistsWorkAndRequestsForegroundServiceWithoutRunningRepositoryWorker() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val serviceController = FakeServiceController()
        var sourceCalls = 0
        val manager = manager(
            scope = scope,
            serviceController = serviceController,
            source = ThreadPostsSource { _, _ ->
                sourceCalls++
                error("The repository worker must be owned by the service")
            },
        )
        try {
            val request = testRequest()

            manager.enqueue(request)

            assertEquals(1, serviceController.startCalls)
            assertFalse(manager.queueState.value.isRunning)
            assertFalse(sourceCalls > 0)
            assertEquals(listOf(request.key), manager.queueState.value.queuedKeys)
            assertEquals(ThreadDownloadPhase.QUEUED, manager.statuses.value[request.key]?.phase)
        } finally {
            manager.close()
            scope.cancel()
        }
    }

    @Test
    fun serviceStartRunsWorkAfterManagerHasPersistedIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val serviceController = FakeServiceController()
        val thread = testThread(replyCount = 0)
        val manager = manager(
            scope = scope,
            serviceController = serviceController,
            source = ThreadPostsSource { _, page ->
                testPage(
                    thread = thread,
                    page = page,
                    totalPages = 1,
                    posts = listOf(testPost(thread.id, 2000, 1)),
                )
            },
        )
        var generation: Long? = null
        try {
            val request = testRequest(thread)
            manager.enqueue(request)

            generation = manager.downloaderStart()

            val completed = withTimeout(5_000) {
                manager.statuses.map { it[request.key] }.first {
                    it?.phase == ThreadDownloadPhase.COMPLETED
                }
            }
            assertTrue(completed?.completed != null)
        } finally {
            generation?.let(manager::downloaderStop)
            manager.close()
            scope.cancel()
        }
    }

    @Test
    fun staleServiceStopDoesNotCancelNewerServiceGeneration() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val serviceController = FakeServiceController()
        val thread = testThread(replyCount = 0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val manager = manager(
            scope = scope,
            serviceController = serviceController,
            source = ThreadPostsSource { _, _ ->
                calls++
                started.complete(Unit)
                release.await()
                testPage(
                    thread = thread,
                    page = 1,
                    totalPages = 1,
                    posts = listOf(testPost(thread.id, 2000, 1)),
                )
            },
        )
        var newestGeneration: Long? = null
        try {
            val request = testRequest(thread)
            manager.enqueue(request)
            val staleGeneration = manager.downloaderStart()
            withTimeout(5_000) { started.await() }
            newestGeneration = manager.downloaderStart()

            manager.downloaderStop(staleGeneration)
            assertTrue(manager.queueState.value.isRunning)
            release.complete(Unit)

            withTimeout(5_000) {
                manager.statuses.map { it[request.key] }.first {
                    it?.phase == ThreadDownloadPhase.COMPLETED
                }
            }
            assertEquals(1, calls)
        } finally {
            release.complete(Unit)
            newestGeneration?.let(manager::downloaderStop)
            manager.close()
            scope.cancel()
        }
    }

    @Test
    fun pauseAndResumeDelegateForegroundServiceLifetimeToManager() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val serviceController = FakeServiceController()
        val manager = manager(
            scope = scope,
            serviceController = serviceController,
            source = ThreadPostsSource { _, _ -> error("The service was not started in this test") },
        )
        try {
            manager.enqueue(testRequest())

            assertTrue(manager.pauseDownloads())
            assertEquals(1, serviceController.stopCalls)
            assertTrue(manager.queueState.value.isPaused)

            assertTrue(manager.resumeDownloads())
            assertEquals(2, serviceController.startCalls)
            assertFalse(manager.queueState.value.isPaused)
        } finally {
            manager.close()
            scope.cancel()
        }
    }

    private fun manager(
        scope: CoroutineScope,
        serviceController: ThreadDownloadServiceController,
        source: ThreadPostsSource,
    ): ThreadDownloadManager = ThreadDownloadManager(
        repository = ThreadDownloadRepository(
            store = ThreadDownloadFileStore(temporaryFolder.newFolder("downloads")),
            threadPostsSource = source,
            imageDownloader = PostImageDownloader { _, _ -> error("Image download was not expected") },
            scope = scope,
            autoStart = false,
        ),
        serviceController = serviceController,
        scope = scope,
    )

    private class FakeServiceController : ThreadDownloadServiceController {
        var startCalls = 0
        var stopCalls = 0

        override fun start(): Boolean {
            startCalls++
            return true
        }

        override fun stop() {
            stopCalls++
        }
    }
}
