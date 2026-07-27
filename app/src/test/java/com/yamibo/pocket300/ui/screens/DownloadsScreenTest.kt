package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.data.download.ThreadDownloadKey
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DownloadsScreenTest {
    private val downloads = listOf(
        downloadItem(
            threadId = 101,
            subject = "Summer Story",
            author = "Alice",
            downloadedAt = 100,
        ),
        downloadItem(
            threadId = 102,
            subject = "冬日物语",
            author = "Bob",
            downloadedAt = 300,
        ),
        downloadItem(
            threadId = 103,
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
    fun filtersDownloadsByOriginalPoster() {
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
    fun queueOrderTakesPrecedenceOverSavedTime() {
        val firstQueued = downloadItem(
            threadId = 201,
            subject = "First queued",
            author = "Alice",
            downloadedAt = 100,
            phase = ThreadDownloadPhase.QUEUED,
        )
        val secondQueued = downloadItem(
            threadId = 202,
            subject = "Second queued",
            author = "Bob",
            downloadedAt = 200,
            phase = ThreadDownloadPhase.QUEUED,
        )
        val completed = downloadItem(
            threadId = 203,
            subject = "Completed",
            author = "Carol",
            downloadedAt = 300,
        )

        assertEquals(
            listOf(secondQueued, firstQueued, completed),
            filterAndSortDownloads(
                downloads = listOf(firstQueued, completed, secondQueued),
                query = "",
                queueOrder = listOf(secondQueued.key, firstQueued.key),
            ),
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

    @Test
    fun usesTheSameSharedContentKeyAsEveryThreadEntryPoint() {
        assertEquals("thread-101", threadSharedContentKey(101))
    }

    @Test
    fun oneListItemRepresentsTheWholeThread() {
        val item = downloadItem(
            threadId = 123,
            subject = "Whole thread",
            author = "Original poster",
            downloadedAt = 400,
            postCount = 42,
            imageCount = 9,
            phase = ThreadDownloadPhase.FETCHING_PAGES,
        )

        assertEquals(ThreadDownloadKey(123), item.key)
        assertEquals(42, item.postCount)
        assertEquals(9, item.imageCount)
        assertEquals(ThreadDownloadPhase.FETCHING_PAGES, item.phase)
    }

    private fun downloadItem(
        threadId: Int,
        subject: String,
        author: String,
        downloadedAt: Long,
        postCount: Int = 1,
        imageCount: Int = 0,
        phase: ThreadDownloadPhase = ThreadDownloadPhase.COMPLETED,
    ) = DownloadListItem(
        key = ThreadDownloadKey(threadId),
        subject = subject,
        author = author,
        postCount = postCount,
        imageCount = imageCount,
        completedPages = 0,
        totalPages = 0,
        completedImages = 0,
        sizeBytes = 1,
        downloadedAt = downloadedAt,
        phase = phase,
    )
}
