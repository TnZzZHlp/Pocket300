package com.yamibo.pocket300.ui

import com.yamibo.pocket300.data.ReadingHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingHistoryUiTest {
    @Test
    fun weakensThreadsWithReadingHistory() {
        assertEquals(READ_THREAD_ALPHA, threadAlpha(hasReadingHistory = true), 0f)
        assertEquals(1f, threadAlpha(hasReadingHistory = false), 0f)
    }

    @Test
    fun findsLastReadFloorForThreadWithReadingHistory() {
        val histories = mapOf(12 to historyEntry(threadId = 12, lastReadFloor = 8))

        assertEquals(8, lastReadFloor(12, histories))
    }

    @Test
    fun omitsLastReadFloorForThreadWithoutReadingHistory() {
        assertNull(lastReadFloor(13, mapOf(12 to historyEntry(threadId = 12, lastReadFloor = 8))))
    }

    @Test
    fun addsLastReadFloorToRouteWithoutArguments() {
        assertEquals("thread/12?floor=8", routeWithLastReadFloor("thread/12", 8))
    }

    @Test
    fun appendsLastReadFloorToExistingArguments() {
        assertEquals(
            "thread/12?favoriteId=3&floor=8",
            routeWithLastReadFloor("thread/12?favoriteId=3", 8),
        )
    }

    @Test
    fun coercesInvalidFloorToFirstFloor() {
        assertEquals("thread/12?floor=1", routeWithLastReadFloor("thread/12", 0))
    }

    private fun historyEntry(
        threadId: Int,
        lastReadFloor: Int,
    ) = ReadingHistoryEntry(
        threadId = threadId,
        forumId = 300,
        subject = "主题",
        authorName = "作者",
        lastPostAtText = "刚刚",
        lastReadFloor = lastReadFloor,
        readAt = 0,
    )
}
