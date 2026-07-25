package com.yamibo.pocket300.ui.viewmodels

import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostAuthor
import com.yamibo.pocket300.api.YamiboPostComment
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
        ratings = emptyList(),
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
