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
    fun offersRatingsActionForRatedPost() {
        assertFalse(shouldShowRatingsAction(ratingCount = 0))
        assertTrue(shouldShowRatingsAction(ratingCount = 1))
        assertTrue(shouldShowRatingsAction(ratingCount = 4))
    }

    @Test
    fun offersMarkUnreadForReadThread() {
        assertEquals(ThreadReadAction.MARK_UNREAD, threadReadAction(isRead = true))
    }

    @Test
    fun offersMarkReadForUnreadThread() {
        assertEquals(ThreadReadAction.MARK_READ, threadReadAction(isRead = false))
    }

    @Test
    fun keepsNewReplyOnCurrentPageUntilPageBoundary() {
        assertEquals(1, pageForNewReply(totalPosts = 19, pageSize = 20))
        assertEquals(2, pageForNewReply(totalPosts = 20, pageSize = 20))
    }
}
