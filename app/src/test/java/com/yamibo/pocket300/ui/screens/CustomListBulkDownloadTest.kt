package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.data.CustomListThread
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
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
    fun queuesNewThreadsAndRetriesFailedThreadsWithoutFetchingMetadata() = runBlocking {
        val retried = mutableListOf<Int>()
        val enqueued = mutableListOf<CustomListThread>()
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
            enqueueIfMissing = {
                enqueued += it
                true
            },
        )

        assertEquals(listOf(200), retried)
        assertEquals(listOf(100), enqueued.map(CustomListThread::threadId))
        assertEquals(2, result.queuedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(emptySet<Int>(), result.failedThreadIds)
    }

    @Test
    fun enqueueFailureDoesNotBlockLaterThreads() = runBlocking {
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = enqueueCustomListThreadDownloads(
            threads = listOf(thread(100), thread(200)),
            actionFor = { CustomListBulkDownloadAction.PREPARE },
            retry = { false },
            enqueueIfMissing = {
                if (it.threadId == 100) throw IOException("denied")
                true
            },
            onProgress = { completed, total -> progress += completed to total },
        )

        assertEquals(1, result.queuedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(setOf(100), result.failedThreadIds)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }

    @Test
    fun queuesOriginalListThread() = runBlocking {
        val selected = thread(100)
        var enqueued: CustomListThread? = null

        val result = enqueueCustomListThreadDownloads(
            threads = listOf(selected),
            actionFor = { CustomListBulkDownloadAction.PREPARE },
            retry = { false },
            enqueueIfMissing = {
                enqueued = it
                true
            },
        )

        assertEquals(selected, enqueued)
        assertEquals(1, result.queuedCount)
        assertEquals(emptySet<Int>(), result.failedThreadIds)
    }

    @Test
    fun missingRetryRequestFallsBackToEnqueueAndRaceIsCountedAsSkipped() = runBlocking {
        val enqueued = mutableListOf<CustomListThread>()

        val result = enqueueCustomListThreadDownloads(
            threads = listOf(thread(100)),
            actionFor = { CustomListBulkDownloadAction.RETRY },
            retry = { false },
            enqueueIfMissing = {
                enqueued += it
                false
            },
        )

        assertEquals(listOf(100), enqueued.map(CustomListThread::threadId))
        assertEquals(0, result.queuedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(emptySet<Int>(), result.failedThreadIds)
    }

    @Test(expected = CancellationException::class)
    fun cancellationStopsBulkQueueing() = runBlocking {
        enqueueCustomListThreadDownloads(
            threads = listOf(thread(100), thread(200)),
            actionFor = { CustomListBulkDownloadAction.PREPARE },
            retry = { false },
            enqueueIfMissing = { throw CancellationException("cancelled") },
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
