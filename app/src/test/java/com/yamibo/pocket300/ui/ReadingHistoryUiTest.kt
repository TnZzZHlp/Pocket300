package com.yamibo.pocket300.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingHistoryUiTest {
    @Test
    fun weakensThreadsWithReadingHistory() {
        assertEquals(READ_THREAD_ALPHA, threadAlpha(hasReadingHistory = true), 0f)
        assertEquals(1f, threadAlpha(hasReadingHistory = false), 0f)
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
}
