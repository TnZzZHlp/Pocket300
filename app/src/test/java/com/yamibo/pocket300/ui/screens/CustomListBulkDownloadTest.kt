package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.data.CustomListThread
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
import com.yamibo.pocket300.data.download.testThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class CustomListBulkDownloadTest {
    @Test
    fun mapsDownloadStateToBulkAction() {
        assertEquals(
            CustomListBulkDownloadAction.PREPARE,
            customListBulkDownloadAction(phase = null, hasCompletedDownload = false),
        )
        assertEquals(
            CustomListBulkDownloadAction.RETRY,
            customListBulkDownloadAction(
                phase = ThreadDownloadPhase.FAILED,
                hasCompletedDownload = false,
            ),
        )
        listOf(
            ThreadDownloadPhase.QUEUED,
            ThreadDownloadPhase.FETCHING_PAGES,
            ThreadDownloadPhase.DOWNLOADING_IMAGES,
            ThreadDownloadPhase.COMPLETED,
        ).forEach { phase ->
            assertEquals(
                CustomListBulkDownloadAction.SKIP,
                customListBulkDownloadAction(phase, hasCompletedDownload = false),
            )
        }
        assertEquals(
            CustomListBulkDownloadAction.SKIP,
            customListBulkDownloadAction(
                phase = ThreadDownloadPhase.FAILED,
                hasCompletedDownload = true,
            ),
        )
    }

    @Test
    fun keepsSelectedThreadsInDisplayOrder() {
        val displayed = listOf(thread(300), thread(100), thread(200))

        val selected = selectedCustomListThreadsInDisplayOrder(
            displayedThreads = displayed,
            selectedThreadIds = setOf(100, 300),
        )

        assertEquals(listOf(300, 100), selected.map(CustomListThread::threadId))
    }

    @Test
    fun preparesNewThreadsAndRetriesFailedThreadsWithoutAnotherMetadataRequest() = runBlocking {
        val loaded = mutableListOf<Int>()
        val retried = mutableListOf<Int>()
        val enqueued = mutableListOf<Int>()
        val actions = mapOf(
            100 to CustomListBulkDownloadAction.PREPARE,
            200 to CustomListBulkDownloadAction.RETRY,
            300 to CustomListBulkDownloadAction.SKIP,
        )

        val result = enqueueCustomListThreadDownloads(
            threads = listOf(thread(100), thread(200), thread(300)),
            actionFor = { actions.getValue(it) },
            retry = {
                retried += it
                true
            },
            loadThreadDetails = {
                loaded += it
                testThread(threadId = it)
            },
            enqueueIfMissing = {
                enqueued += it.id
                true
            },
        )

        assertEquals(listOf(100), loaded)
        assertEquals(listOf(200), retried)
        assertEquals(listOf(100), enqueued)
        assertEquals(2, result.queuedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(emptySet<Int>(), result.failedThreadIds)
    }

    @Test
    fun preparationFailureDoesNotBlockLaterThreads() = runBlocking {
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = enqueueCustomListThreadDownloads(
            threads = listOf(thread(100), thread(200)),
            actionFor = { CustomListBulkDownloadAction.PREPARE },
            retry = { false },
            loadThreadDetails = {
                if (it == 100) throw IOException("denied")
                testThread(threadId = it)
            },
            enqueueIfMissing = { true },
            onProgress = { completed, total -> progress += completed to total },
        )

        assertEquals(1, result.queuedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(setOf(100), result.failedThreadIds)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun mismatchedThreadDetailsFailWithoutEnqueueing() = runBlocking {
        val result = enqueueCustomListThreadDownloads(
            threads = listOf(thread(100)),
            actionFor = { CustomListBulkDownloadAction.PREPARE },
            retry = { false },
            loadThreadDetails = { testThread(threadId = 999) },
            enqueueIfMissing = { error("must not enqueue another thread") },
        )

        assertEquals(0, result.queuedCount)
        assertEquals(setOf(100), result.failedThreadIds)
    }

    @Test
    fun missingRetryRequestFallsBackToPreparationAndRaceIsCountedAsSkipped() = runBlocking {
        val loaded = mutableListOf<Int>()
        val enqueued = mutableListOf<Int>()

        val result = enqueueCustomListThreadDownloads(
            threads = listOf(thread(100)),
            actionFor = { CustomListBulkDownloadAction.RETRY },
            retry = { false },
            loadThreadDetails = {
                loaded += it
                testThread(threadId = it)
            },
            enqueueIfMissing = {
                enqueued += it.id
                false
            },
        )

        assertEquals(listOf(100), loaded)
        assertEquals(listOf(100), enqueued)
        assertEquals(0, result.queuedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(emptySet<Int>(), result.failedThreadIds)
    }

    @Test(expected = CancellationException::class)
    fun cancellationStopsBulkPreparation() = runBlocking {
        enqueueCustomListThreadDownloads(
            threads = listOf(thread(100), thread(200)),
            actionFor = { CustomListBulkDownloadAction.PREPARE },
            retry = { false },
            loadThreadDetails = { throw CancellationException("cancelled") },
            enqueueIfMissing = { true },
        )
        Unit
    }

    private fun thread(id: Int) = CustomListThread(
        listId = 1,
        threadId = id,
        forumId = 300,
        forumName = "测试",
        subject = "主题 $id",
        authorName = "作者",
        createdAtText = "今天",
        excerpt = null,
        replyCount = 0,
        viewCount = 0,
        webUrl = "https://bbs.yamibo.com/thread-$id-1-1.html",
    )
}
