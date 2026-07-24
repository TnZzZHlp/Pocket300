package com.yamibo.pocket300.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadScreenTest {
    @Test
    fun hidesTopBarTitleWhileThreadHeroIsVisible() {
        assertFalse(shouldShowThreadTitle(firstVisibleItemIndex = 0))
    }

    @Test
    fun showsTopBarTitleAfterThreadHeroScrollsOut() {
        assertTrue(shouldShowThreadTitle(firstVisibleItemIndex = 1))
    }

    @Test
    fun offersMarkUnreadForReadThread() {
        assertEquals(ThreadReadAction.MARK_UNREAD, threadReadAction(isRead = true))
    }

    @Test
    fun offersMarkReadForUnreadThread() {
        assertEquals(ThreadReadAction.MARK_READ, threadReadAction(isRead = false))
    }
}
