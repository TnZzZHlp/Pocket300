package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.api.YamiboPostRatingForm
import com.yamibo.pocket300.api.YamiboPostRatingOption
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
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
    fun showsRatingsSummaryOnlyForRatedPost() {
        assertFalse(shouldShowRatingsSummary(ratingCount = 0))
        assertTrue(shouldShowRatingsSummary(ratingCount = 1))
        assertTrue(shouldShowRatingsSummary(ratingCount = 4))
    }

    @Test
    fun limitsPostRatingScoresByRangeAndRemainingBalance() {
        assertEquals(
            listOf(-2, -1, 1, 2),
            postRatingScoreChoices(ratingOption(minScore = -5, maxScore = 4, remainingToday = 2)),
        )
        assertEquals(
            listOf(1, 2, 3),
            postRatingScoreChoices(ratingOption(minScore = 1, maxScore = 5, remainingToday = 3)),
        )
        assertTrue(
            postRatingScoreChoices(
                ratingOption(minScore = -3, maxScore = 3, remainingToday = 0),
            ).isEmpty(),
        )
    }

    @Test
    fun ratingScoreControlsPassThroughZeroBetweenNegativeAndPositiveValues() {
        val option = ratingOption(minScore = -2, maxScore = 2, remainingToday = 2)

        assertEquals(-1, adjacentPostRatingScore(option, current = 0, direction = -1))
        assertEquals(0, adjacentPostRatingScore(option, current = -1, direction = 1))
        assertEquals(1, adjacentPostRatingScore(option, current = 0, direction = 1))
        assertEquals(0, adjacentPostRatingScore(option, current = 1, direction = -1))
    }

    @Test
    fun enablesRatingSubmissionOnlyForAllowedNonZeroScore() {
        val option = ratingOption(minScore = -3, maxScore = 3, remainingToday = 2)
        val form = YamiboPostRatingForm(
            threadId = 1000,
            postId = 9,
            formHash = "hash",
            referer = "https://bbs.yamibo.com/thread-1000-1-1.html",
            options = listOf(option),
            reasonSuggestions = emptyList(),
            sendReasonPmByDefault = false,
            sendReasonPmLocked = false,
        )

        assertFalse(canSubmitPostRating(form, mapOf(option.creditId to 0)))
        assertTrue(canSubmitPostRating(form, mapOf(option.creditId to -2)))
        assertFalse(canSubmitPostRating(form, mapOf(option.creditId to 3)))
    }

    @Test
    fun rejectsUnknownOrInvalidAdditionalRatingScores() {
        val option = ratingOption(minScore = -2, maxScore = 2, remainingToday = 2)
        val secondOption = option.copy(creditId = 2, creditName = "贡献")
        val form = YamiboPostRatingForm(
            threadId = 1000,
            postId = 9,
            formHash = "hash",
            referer = "https://bbs.yamibo.com/thread-1000-1-1.html",
            options = listOf(option, secondOption),
            reasonSuggestions = emptyList(),
            sendReasonPmByDefault = false,
            sendReasonPmLocked = false,
        )

        assertFalse(canSubmitPostRating(form, mapOf(option.creditId to 1, 2 to 3)))
        assertFalse(canSubmitPostRating(form, mapOf(option.creditId to 1, 99 to 1)))
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
    fun mapsThreadDownloadPhasesToTopBarActions() {
        assertEquals(ThreadDownloadAction.DOWNLOAD, threadDownloadAction(null))
        assertEquals(
            ThreadDownloadAction.DOWNLOADING,
            threadDownloadAction(ThreadDownloadPhase.QUEUED),
        )
        assertEquals(
            ThreadDownloadAction.DOWNLOADING,
            threadDownloadAction(ThreadDownloadPhase.FETCHING_PAGES),
        )
        assertEquals(
            ThreadDownloadAction.DOWNLOADING,
            threadDownloadAction(ThreadDownloadPhase.DOWNLOADING_IMAGES),
        )
        assertEquals(
            ThreadDownloadAction.RETRY,
            threadDownloadAction(ThreadDownloadPhase.FAILED),
        )
        assertEquals(
            ThreadDownloadAction.DOWNLOADED,
            threadDownloadAction(ThreadDownloadPhase.COMPLETED),
        )
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

    @Test
    fun keepsNewReplyOnCurrentPageUntilPageBoundary() {
        assertEquals(1, pageForNewReply(totalPosts = 19, pageSize = 20))
        assertEquals(2, pageForNewReply(totalPosts = 20, pageSize = 20))
    }

    private fun ratingOption(
        minScore: Int,
        maxScore: Int,
        remainingToday: Int,
    ) = YamiboPostRatingOption(
        creditId = 1,
        creditName = "百合币",
        minScore = minScore,
        maxScore = maxScore,
        remainingToday = remainingToday,
    )
}
