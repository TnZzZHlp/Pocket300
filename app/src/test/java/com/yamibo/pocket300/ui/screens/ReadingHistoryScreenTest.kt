package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.data.ReadingHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingHistoryScreenTest {
    private val entries = listOf(
        historyEntry(101, "Summer Story", "Alice"),
        historyEntry(102, "冬日物语", "Bob"),
    )

    @Test
    fun filtersReadingHistoryByTitleIgnoringCaseAndWhitespace() {
        assertEquals(listOf(entries[0]), filterReadingHistoryEntries(entries, "  summer  "))
    }

    @Test
    fun filtersReadingHistoryByAuthor() {
        assertEquals(listOf(entries[1]), filterReadingHistoryEntries(entries, "bob"))
    }

    @Test
    fun requiresEverySearchTermToMatchReadingHistoryContent() {
        assertEquals(listOf(entries[0]), filterReadingHistoryEntries(entries, "Summer Alice"))
        assertEquals(emptyList<ReadingHistoryEntry>(), filterReadingHistoryEntries(entries, "Summer Bob"))
    }

    @Test
    fun blankHistoryQueryKeepsEveryEntry() {
        assertEquals(entries, filterReadingHistoryEntries(entries, "  "))
    }

    private fun historyEntry(
        threadId: Int,
        subject: String,
        authorName: String,
    ) = ReadingHistoryEntry(
        threadId = threadId,
        forumId = 300,
        subject = subject,
        authorName = authorName,
        lastPostAtText = "昨天",
        lastReadFloor = 2,
        readAt = 1L,
    )
}
