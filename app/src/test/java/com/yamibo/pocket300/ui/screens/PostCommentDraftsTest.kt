package com.yamibo.pocket300.ui.screens

import androidx.compose.runtime.saveable.SaverScope
import com.yamibo.pocket300.api.POST_COMMENT_MAX_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PostCommentDraftsTest {
    @Test
    fun preservesDraftWhitespaceWhileEditingAnotherPost() {
        val drafts = PostCommentDrafts()
        drafts.update(12, "  Unsent comment\n")
        drafts.update(34, "Another draft")
        drafts.clear(34)

        assertEquals("  Unsent comment\n", drafts[12])
    }

    @Test
    fun isolatesDraftsBetweenPosts() {
        val drafts = PostCommentDrafts()
        drafts.update(12, "First post")
        drafts.update(34, "Second post")

        assertEquals("First post", drafts[12])
        assertEquals("Second post", drafts[34])
        assertEquals("", drafts[56])
    }

    @Test
    fun clearsOnlyTheSuccessfullySubmittedPost() {
        val drafts = PostCommentDrafts()
        drafts.update(12, "Submitted")
        drafts.update(34, "Unsent")
        drafts.clear(12)

        assertEquals("", drafts[12])
        assertEquals("Unsent", drafts[34])
    }

    @Test
    fun rejectsOverlongEditsWithoutLosingExistingDraft() {
        val drafts = PostCommentDrafts()
        val limit = "a".repeat(POST_COMMENT_MAX_LENGTH)
        drafts.update(12, limit)
        drafts.update(12, limit + "b")

        assertEquals(limit, drafts[12])
        drafts.update(12, "")
        assertEquals("", drafts[12])
    }

    @Test
    fun ignoresEditsWithoutAValidPostTarget() {
        val drafts = PostCommentDrafts()
        drafts.update(0, "No target")
        drafts.update(-1, "Invalid target")

        assertEquals("", drafts[0])
        assertEquals("", drafts[-1])
    }

    @Test
    fun restoresAllTargetDraftsAfterStateRecreation() {
        val drafts = PostCommentDrafts()
        drafts.update(12, "First draft")
        drafts.update(34, "第二份草稿")
        val saved = with(PostCommentDrafts.Saver) {
            SaverScope { true }.save(drafts)
        }
        assertNotNull(saved)
        val restored = requireNotNull(PostCommentDrafts.Saver.restore(requireNotNull(saved)))

        assertEquals("First draft", restored[12])
        assertEquals("第二份草稿", restored[34])
        restored.clear(12)
        assertEquals("", restored[12])
        assertEquals("第二份草稿", restored[34])
    }
}
