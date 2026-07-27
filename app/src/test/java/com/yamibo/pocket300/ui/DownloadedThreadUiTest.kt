package com.yamibo.pocket300.ui

import com.yamibo.pocket300.data.download.DownloadedThread
import com.yamibo.pocket300.data.download.ThreadDownloadManifest
import com.yamibo.pocket300.data.download.ThreadDownloadSnapshot
import com.yamibo.pocket300.data.download.testPost
import com.yamibo.pocket300.data.download.testThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadedThreadUiTest {
    @Test
    fun showsIndicatorForDownloadedThread() {
        assertTrue(shouldShowDownloadedIndicator(101, setOf(101)))
    }

    @Test
    fun hidesIndicatorForUndownloadedThread() {
        assertFalse(shouldShowDownloadedIndicator(102, setOf(101)))
    }

    @Test
    fun buildsDistinctCompletedThreadIds() {
        val first = downloadedThread(101)
        val second = downloadedThread(102)
        val ids = completedThreadIds(listOf(first, second, first))

        assertEquals(linkedSetOf(101, 102), ids)
    }

    private fun downloadedThread(threadId: Int): DownloadedThread = DownloadedThread(
        manifest = ThreadDownloadManifest(
            snapshot = ThreadDownloadSnapshot(
                thread = testThread(threadId = threadId, replyCount = 0),
                poll = null,
                posts = listOf(
                    testPost(
                        threadId = threadId,
                        postId = threadId * 10,
                        position = 1,
                    ),
                ),
                capturedPageCount = 1,
                sourcePageSize = 20,
                sourceTotalPosts = 1,
            ),
            images = emptyList(),
            requestedAt = 1L,
            completedAt = 2L,
        ),
        directory = File("downloads-$threadId"),
    )
}
