package com.yamibo.pocket300.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.setValue
import com.yamibo.pocket300.api.POST_COMMENT_MAX_LENGTH

/** Page-local drafts are keyed by post so dismissing or changing targets never mixes text. */
internal class PostCommentDrafts(initial: Map<Int, String> = emptyMap()) {
    private var drafts by mutableStateOf(initial)

    operator fun get(postId: Int): String = drafts[postId].orEmpty()

    fun update(postId: Int, text: String) {
        if (postId <= 0 || text.length > POST_COMMENT_MAX_LENGTH) return
        drafts = if (text.isEmpty()) drafts - postId else drafts + (postId to text)
    }

    fun clear(postId: Int) {
        drafts = drafts - postId
    }

    companion object {
        val Saver = mapSaver(
            save = { state: PostCommentDrafts -> state.drafts.mapKeys { it.key.toString() } },
            restore = { saved ->
                PostCommentDrafts(saved.mapKeys { it.key.toInt() }.mapValues { it.value as String })
            },
        )
    }
}
