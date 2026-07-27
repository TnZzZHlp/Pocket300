package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostRatingForm
import com.yamibo.pocket300.api.YamiboPostRatingOption
import com.yamibo.pocket300.data.download.DownloadedThread
import com.yamibo.pocket300.data.download.ThreadDownloadManifest
import com.yamibo.pocket300.data.download.ThreadDownloadPhase
import com.yamibo.pocket300.data.download.ThreadDownloadSnapshot
import com.yamibo.pocket300.data.download.testAuthor
import com.yamibo.pocket300.data.download.testPage
import com.yamibo.pocket300.data.download.testPoll
import com.yamibo.pocket300.data.download.testPost
import com.yamibo.pocket300.data.download.testThread
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.viewmodels.ThreadContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    @Test
    fun showsDownloadedThreadWhileBackgroundRefreshIsLoading() {
        val local = threadContent(subject = "Downloaded")

        val state = resolveThreadDisplayState(
            offlineOnly = false,
            localState = LoadState.Ready(local),
            networkState = LoadState.Loading,
        )

        assertSame(local, (state as LoadState.Ready).value)
    }

    @Test
    fun keepsDownloadedThreadWhenBackgroundRefreshFails() {
        val local = threadContent(subject = "Downloaded")

        val state = resolveThreadDisplayState(
            offlineOnly = false,
            localState = LoadState.Ready(local),
            networkState = LoadState.Failed("network unavailable"),
        )

        assertSame(local, (state as LoadState.Ready).value)
    }

    @Test
    fun overlaysFreshPostsWithoutDroppingUnrefreshedDownloadedFloors() {
        val cachedFirst = testPost(postId = 2000, position = 1, html = "<p>cached</p>")
        val cachedSecond = testPost(postId = 2001, position = 2)
        val freshFirst = cachedFirst.copy(html = "<p>fresh</p>")
        val freshThird = testPost(postId = 2002, position = 3)
        val local = threadContent(
            subject = "Downloaded",
            posts = listOf(cachedFirst, cachedSecond),
        )
        val network = threadContent(
            subject = "Fresh",
            posts = listOf(freshFirst, freshThird),
        )

        val state = resolveThreadDisplayState(
            offlineOnly = false,
            localState = LoadState.Ready(local),
            networkState = LoadState.Ready(network),
        )

        val content = (state as LoadState.Ready).value
        assertEquals("Fresh", content.page.thread.subject)
        assertEquals(listOf(2000, 2001, 2002), content.posts.map { it.id })
        assertEquals("<p>fresh</p>", content.posts[0].html)
        assertSame(cachedSecond, content.posts[1])
        assertEquals(content.posts, content.page.posts)
    }

    @Test
    fun usesNetworkFailureWhenNoDownloadedThreadExists() {
        val failure = LoadState.Failed("network unavailable")

        val state = resolveThreadDisplayState(
            offlineOnly = false,
            localState = LoadState.Failed("not downloaded"),
            networkState = failure,
        )

        assertSame(failure, state)
    }

    @Test
    fun waitsForLocalProbeBeforeUsingAnAlreadyReadyNetworkState() {
        val network = LoadState.Ready(threadContent(subject = "Fresh"))

        val state = resolveThreadDisplayState(
            offlineOnly = false,
            localState = LoadState.Loading,
            networkState = network,
        )

        assertSame(LoadState.Loading, state)
    }

    @Test
    fun strictOfflineModeNeverUsesNetworkContent() {
        val local = LoadState.Ready(threadContent(subject = "Downloaded"))

        val state = resolveThreadDisplayState(
            offlineOnly = true,
            localState = local,
            networkState = LoadState.Ready(threadContent(subject = "Fresh")),
        )

        assertSame(local, state)
    }

    @Test
    fun localMissUsesSuccessfulNetworkContent() {
        val network = LoadState.Ready(threadContent(subject = "Fresh"))

        val state = resolveThreadDisplayState(
            offlineOnly = false,
            localState = LoadState.Failed("not downloaded"),
            networkState = network,
        )

        assertSame(network.value, (state as LoadState.Ready).value)
    }

    @Test
    fun strictOfflineModeReportsMissingDownload() {
        val missing = LoadState.Failed("not downloaded")

        val state = resolveThreadDisplayState(
            offlineOnly = true,
            localState = missing,
            networkState = LoadState.Ready(threadContent(subject = "Fresh")),
        )

        assertSame(missing, state)
    }

    @Test
    fun probesLocalContentBeforeStartingNetworkRefresh() = runBlocking {
        val events = mutableListOf<String>()

        probeLocalFirstThread<String>(
            offlineOnly = false,
            loadLocal = {
                events += "read-local"
                "downloaded"
            },
            onLocalResolved = { events += "emit-$it" },
            startNetwork = { events += "start-network" },
        )

        assertEquals(
            listOf("read-local", "emit-downloaded", "start-network"),
            events,
        )
    }

    @Test
    fun strictOfflineProbeNeverStartsNetworkRefresh() = runBlocking {
        var networkStarted = false

        probeLocalFirstThread<String>(
            offlineOnly = true,
            loadLocal = { null },
            onLocalResolved = {},
            startNetwork = { networkStarted = true },
        )

        assertFalse(networkStarted)
    }

    @Test
    fun downloadedThreadConversionKeepsEveryFloorPollAndPagination() {
        val thread = testThread(replyCount = 2)
        val original = testPost(postId = 2000, position = 1).copy(author = thread.author)
        val reply = testPost(postId = 2001, position = 2).copy(author = testAuthor(id = 99))
        val originalFollowUp = testPost(postId = 2002, position = 3).copy(
            author = thread.author,
            isOriginalPost = true,
        )
        val poll = testPoll()
        val downloaded = DownloadedThread(
            manifest = ThreadDownloadManifest(
                snapshot = ThreadDownloadSnapshot(
                    thread = thread,
                    poll = poll,
                    posts = listOf(original, reply, originalFollowUp),
                    capturedPageCount = 2,
                    sourcePageSize = 2,
                    sourceTotalPosts = 3,
                ),
                images = emptyList(),
                requestedAt = 1L,
                completedAt = 2L,
            ),
            directory = File("downloads"),
        )

        val content = downloaded.toThreadContent()
        val originalPosterOnly = downloaded.toThreadContent(authorId = thread.author.id)

        assertEquals(listOf(2000, 2001, 2002), content.posts.map(YamiboPost::id))
        assertSame(poll, content.page.poll)
        assertEquals(2, content.page.pagination.totalPages)
        assertEquals(3, content.page.pagination.totalPosts)
        assertEquals(listOf(2000, 2002), originalPosterOnly.posts.map(YamiboPost::id))
        assertEquals(2, originalPosterOnly.page.pagination.totalPosts)
        assertEquals(2, downloadedTargetPage(downloaded, 3, 0, 1))
    }

    private fun threadContent(
        subject: String,
        posts: List<YamiboPost> = listOf(
            testPost(postId = 2000, position = 1),
        ),
    ): ThreadContent {
        val thread = testThread(replyCount = posts.size - 1, subject = subject)
        return ThreadContent(
            page = testPage(
                thread = thread,
                page = 1,
                totalPages = 1,
                posts = posts,
                totalPosts = posts.size,
                hasNextPage = false,
            ),
            posts = posts,
        )
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
