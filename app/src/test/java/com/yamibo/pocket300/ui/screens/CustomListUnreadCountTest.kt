package com.yamibo.pocket300.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomListUnreadCountTest {
    @Test
    fun countsThreadsAbsentFromReadingHistory() {
        assertEquals(
            2,
            unreadCustomListThreadCount(
                threadIds = setOf(100, 200, 300),
                readThreadIds = setOf(100),
            ),
        )
    }

    @Test
    fun returnsZeroWhenEveryThreadHasBeenRead() {
        assertEquals(
            0,
            unreadCustomListThreadCount(
                threadIds = setOf(100, 200),
                readThreadIds = setOf(100, 200, 300),
            ),
        )
    }
}
