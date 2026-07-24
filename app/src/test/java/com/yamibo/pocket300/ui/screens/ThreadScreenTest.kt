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

    @Test
    fun followsServerVisibilityForPollResults() {
        assertFalse(shouldShowPollResults(resultsHiddenUntilVote = true))
        assertTrue(shouldShowPollResults(resultsHiddenUntilVote = false))
    }

    @Test
    fun replacesSelectionForSingleChoicePoll() {
        assertEquals(
            setOf(9),
            togglePollOption(
                selectedOptionIds = setOf(7),
                optionId = 9,
                multiple = false,
                maxChoices = 1,
            ),
        )
    }

    @Test
    fun togglesMultipleChoicePollWithinLimit() {
        assertEquals(
            setOf(7, 9),
            togglePollOption(setOf(7), optionId = 9, multiple = true, maxChoices = 2),
        )
        assertEquals(
            setOf(7),
            togglePollOption(setOf(7, 9), optionId = 9, multiple = true, maxChoices = 2),
        )
        assertEquals(
            setOf(7, 9),
            togglePollOption(setOf(7, 9), optionId = 11, multiple = true, maxChoices = 2),
        )
    }
}
