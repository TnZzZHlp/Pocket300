package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.data.download.PostDownloadKey
import com.yamibo.pocket300.data.download.PostDownloadPhase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DownloadsScreenTest {
    private val downloads = listOf(
        downloadItem(
            threadId = 101,
            postId = 1001,
            subject = "Summer Story",
            author = "Alice",
            downloadedAt = 100,
        ),
        downloadItem(
            threadId = 102,
            postId = 1002,
            subject = "冬日物语",
            author = "Bob",
            downloadedAt = 300,
        ),
        downloadItem(
            threadId = 103,
            postId = 1003,
            subject = "Another Summer",
            author = "Carol",
            downloadedAt = 200,
        ),
    )

    @Test
    fun blankQuerySortsDownloadsByNewestSavedTime() {
        assertEquals(
            listOf(downloads[1], downloads[2], downloads[0]),
            filterAndSortDownloads(downloads, "  "),
        )
    }

    @Test
    fun filtersDownloadsByTitleIgnoringCaseAndWhitespace() {
        assertEquals(
            listOf(downloads[2], downloads[0]),
            filterAndSortDownloads(downloads, "  SUMMER  "),
        )
    }

    @Test
    fun filtersDownloadsByPostAuthor() {
        assertEquals(
            listOf(downloads[1]),
            filterAndSortDownloads(downloads, "bob"),
        )
    }

    @Test
    fun requiresEverySearchTermAcrossTitleAndAuthor() {
        assertEquals(
            listOf(downloads[0]),
            filterAndSortDownloads(downloads, "story alice"),
        )
        assertEquals(
            emptyList<DownloadListItem>(),
            filterAndSortDownloads(downloads, "story bob"),
        )
    }

    @Test
    fun formatsDownloadSizesAtBinaryUnitBoundaries() {
        assertEquals("0 B", formatDownloadSize(-1))
        assertEquals("1023 B", formatDownloadSize(1_023))
        assertEquals("1.0 KB", formatDownloadSize(1_024))
        assertEquals("1.5 MB", formatDownloadSize(1_572_864))
        assertEquals("2.0 GB", formatDownloadSize(2_147_483_648))
    }

    @Test
    fun formatsSavedTimeInRequestedZone() {
        val timestamp = Instant.parse("2026-07-27T03:04:00Z").toEpochMilli()

        assertEquals(
            "2026-07-27 11:04",
            formatDownloadTime(timestamp, ZoneOffset.ofHours(8)),
        )
    }

    private fun downloadItem(
        threadId: Int,
        postId: Int,
        subject: String,
        author: String,
        downloadedAt: Long,
    ) = DownloadListItem(
        key = PostDownloadKey(threadId, postId),
        subject = subject,
        author = author,
        floor = 1,
        isOriginalPost = true,
        hasText = true,
        imageCount = 0,
        completedImages = 0,
        sizeBytes = 1,
        downloadedAt = downloadedAt,
        phase = PostDownloadPhase.COMPLETED,
    )
}
