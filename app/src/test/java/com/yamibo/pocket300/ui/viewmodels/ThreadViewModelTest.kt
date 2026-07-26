package com.yamibo.pocket300.ui.viewmodels

import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboPostComment
import com.yamibo.pocket300.api.YamiboPostRatingForm
import com.yamibo.pocket300.api.YamiboPostRatingOption
import com.yamibo.pocket300.ui.LoadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadViewModelTest {
    @Test
    fun doesNotReloadSameRequestAfterReturningFromReader() {
        val tracker = ThreadPostsRequestTracker()
        val input = GetThreadPostsInput(threadId = 1000, page = 2, authorId = 42)

        assertTrue(tracker.shouldLoad(input))
        assertFalse(tracker.shouldLoad(input))

        tracker.invalidate()
        assertTrue(tracker.shouldLoad(input))
    }

    @Test
    fun replacesOnlyTargetPostCommentsWithoutChangingPostOrder() {
        val posts = listOf(testPost(9), testPost(10))
        val comment = YamiboPostComment(
            author = testAuthor,
            createdAtText = "刚刚",
            id = 3,
            message = "新点评",
            postId = 10,
            threadId = 1000,
        )

        val updated = replacePostComments(posts, postId = 10, comments = listOf(comment))

        assertEquals(listOf(9, 10), updated.map { it.id })
        assertSame(posts[0], updated[0])
        assertEquals(listOf(comment), updated[1].comments)
        assertEquals(posts[1].html, updated[1].html)
    }

    @Test
    fun keepsOriginalPostsWhenCommentTargetIsNotLoaded() {
        val posts = listOf(testPost(9))

        assertSame(posts, replacePostComments(posts, postId = 10, comments = emptyList()))
    }

    @Test
    fun replacesOnlyTargetPostWithoutChangingPostOrder() {
        val posts = listOf(testPost(9), testPost(10), testPost(11))
        val updatedPost = posts[1].copy(
            ratingCount = 2,
            replyCredit = 3,
        )

        val updated = replacePost(posts, updatedPost)

        assertEquals(listOf(9, 10, 11), updated.map { it.id })
        assertSame(posts[0], updated[0])
        assertSame(updatedPost, updated[1])
        assertSame(posts[2], updated[2])
    }

    @Test
    fun keepsOriginalPostsWhenPostTargetIsNotLoaded() {
        val posts = listOf(testPost(9))

        assertSame(posts, replacePost(posts, testPost(10)))
    }

    @Test
    fun exposesSubmittedRatingWhenMobilePostCountIsStale() {
        val updated = withSubmittedPostRating(
            post = testPost(9),
            ratingsCount = 3,
        )

        assertEquals(3, updated.ratingCount)
    }

    @Test
    fun exposesAtLeastOneRatingWhenRatingDetailsAreTemporarilyStale() {
        val updated = withSubmittedPostRating(
            post = testPost(9),
            ratingsCount = 0,
        )

        assertEquals(1, updated.ratingCount)
    }

    @Test
    fun keepsHigherMobileRatingCountAfterSubmission() {
        val post = testPost(9).copy(ratingCount = 4)

        assertSame(
            post,
            withSubmittedPostRating(post = post, ratingsCount = 3),
        )
    }

    @Test
    fun overlaysDeferredPostUpdatesWhenTheirPageLoads() {
        val posts = listOf(testPost(9), testPost(10), testPost(11))
        val updatedNine = posts[0].copy(ratingCount = 2)
        val updatedEleven = posts[2].copy(ratingCount = 4)

        val updated = replacePosts(posts, listOf(updatedNine, updatedEleven))

        assertSame(updatedNine, updated[0])
        assertSame(posts[1], updated[1])
        assertSame(updatedEleven, updated[2])
    }

    @Test
    fun returnsFalseWhenUpdatingPostBeforeContentLoads() {
        assertFalse(ThreadViewModel().updatePost(testPost(9)))
    }

    @Test
    fun keepsUpdatedPostWhenNextPageContainsStaleCopy() {
        val original = testPost(9)
        val updatedPost = original.copy(
            ratingCount = 2,
            replyCredit = 3,
        )
        val updated = replacePost(
            posts = listOf(original),
            post = updatedPost,
        )

        val merged = mergeThreadPosts(
            existing = updated,
            loaded = listOf(original, testPost(10)),
            page = 2,
        )

        assertEquals(listOf(9, 10), merged.map { it.id })
        assertSame(updatedPost, merged.first())
    }

    @Test
    fun keepsRefreshedCommentsWhenNextPageFinishesLoading() {
        val comment = YamiboPostComment(
            author = testAuthor,
            createdAtText = "刚刚",
            id = 3,
            message = "新点评",
            postId = 9,
            threadId = 1000,
        )
        val refreshed = replacePostComments(
            posts = listOf(testPost(9)),
            postId = 9,
            comments = listOf(comment),
        )

        val merged = mergeThreadPosts(
            existing = refreshed,
            loaded = listOf(testPost(9), testPost(10)),
            page = 2,
        )

        assertEquals(listOf(9, 10), merged.map { it.id })
        assertEquals(listOf(comment), merged.first().comments)
    }

    @Test
    fun initializesRatingDraftFromLoadedServerForm() {
        val state = createPostRatingDialogState(testPost(9))
        val form = ratingForm(sendReasonPmByDefault = true, sendReasonPmLocked = true)

        val loaded = withPostRatingFormResult(state, LoadState.Ready(form))

        assertSame(form, (loaded.formState as LoadState.Ready).value)
        assertEquals(mapOf(1 to 0), loaded.scores)
        assertTrue(loaded.sendReasonPm)
        assertEquals("", loaded.reason)
    }

    @Test
    fun keepsRatingDraftWhenFormIsReloaded() {
        val initial = withPostRatingFormResult(
            createPostRatingDialogState(testPost(9)),
            LoadState.Ready(ratingForm()),
        )
        val drafted = withPostRatingReason(
            withPostRatingScore(initial, creditId = 1, score = 2),
            "感谢分享",
        ).let { withPostRatingSendReasonPm(it, true) }

        val reloaded = withPostRatingFormResult(
            drafted,
            LoadState.Ready(ratingForm()),
        )

        assertEquals(mapOf(1 to 2), reloaded.scores)
        assertEquals("感谢分享", reloaded.reason)
        assertTrue(reloaded.sendReasonPm)
    }

    @Test
    fun resetsRatingDraftScoreThatIsNoLongerAllowedAfterFormReload() {
        val initial = withPostRatingFormResult(
            createPostRatingDialogState(testPost(9)),
            LoadState.Ready(ratingForm()),
        )
        val drafted = withPostRatingScore(initial, creditId = 1, score = 2)
        val restrictedForm = ratingForm().copy(
            options = listOf(
                ratingForm().options.single().copy(remainingToday = 1),
            ),
        )

        val reloaded = withPostRatingFormResult(
            drafted,
            LoadState.Ready(restrictedForm),
        )

        assertEquals(mapOf(1 to 0), reloaded.scores)
        assertFalse(canSubmitPostRating(reloaded))
    }

    @Test
    fun ignoresInvalidOrLockedRatingDraftChanges() {
        val loaded = withPostRatingFormResult(
            createPostRatingDialogState(testPost(9)),
            LoadState.Ready(
                ratingForm(sendReasonPmByDefault = true, sendReasonPmLocked = true),
            ),
        )

        assertSame(loaded, withPostRatingScore(loaded, creditId = 1, score = 4))
        assertSame(loaded, withPostRatingSendReasonPm(loaded, false))
        assertSame(loaded, withPostRatingReason(loaded, "x".repeat(41)))
        assertSame(loaded, withPostRatingReason(loaded, "中".repeat(21)))
    }

    @Test
    fun allowsRatingSubmissionOnlyWithAnAllowedNonZeroScore() {
        val loaded = withPostRatingFormResult(
            createPostRatingDialogState(testPost(9)),
            LoadState.Ready(ratingForm()),
        )

        assertFalse(canSubmitPostRating(loaded))
        val selected = withPostRatingScore(loaded, creditId = 1, score = -2)
        assertTrue(canSubmitPostRating(selected))
        assertFalse(canSubmitPostRating(selected.copy(submitting = true)))
    }

    @Test
    fun rejectsRatingSubmissionWhenAnySelectedScoreIsInvalid() {
        val secondOption = ratingForm().options.single().copy(
            creditId = 2,
            creditName = "贡献",
        )
        val form = ratingForm().copy(
            options = ratingForm().options + secondOption,
        )
        val loaded = withPostRatingFormResult(
            createPostRatingDialogState(testPost(9)),
            LoadState.Ready(form),
        ).copy(scores = mapOf(1 to 1, 2 to 3))

        assertFalse(canSubmitPostRating(loaded))
    }

    private fun ratingForm(
        sendReasonPmByDefault: Boolean = false,
        sendReasonPmLocked: Boolean = false,
    ) = YamiboPostRatingForm(
        threadId = 1000,
        postId = 9,
        formHash = "hash",
        referer = "https://bbs.yamibo.com/thread-1000-1-1.html",
        options = listOf(
            YamiboPostRatingOption(
                creditId = 1,
                creditName = "百合币",
                minScore = -3,
                maxScore = 3,
                remainingToday = 2,
            ),
        ),
        reasonSuggestions = listOf("感谢分享"),
        sendReasonPmByDefault = sendReasonPmByDefault,
        sendReasonPmLocked = sendReasonPmLocked,
    )

    private fun testPost(id: Int) = YamiboPost(
        attachments = emptyList(),
        author = testAuthor,
        comments = emptyList(),
        createdAt = 10_000,
        createdAtText = "刚刚",
        html = "<p>正文 $id</p>",
        hasAttachment = false,
        id = id,
        isOriginalPost = id == 9,
        number = id - 8,
        position = id - 8,
        ratingCount = 0,
        replyCredit = 0,
        status = 0,
        threadId = 1000,
    )

    private companion object {
        val testAuthor = YamiboPostAuthor(
            avatarUrl = null,
            groupIconId = null,
            groupId = 10,
            id = 42,
            isAnonymous = false,
            name = "alice",
        )
    }
}
