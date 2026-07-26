package com.yamibo.pocket300.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.YamiboPost
import com.yamibo.pocket300.api.YamiboPostComment
import com.yamibo.pocket300.api.YamiboThreadPostsPage
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class ThreadContent(
    val page: YamiboThreadPostsPage,
    val posts: List<YamiboPost>,
    val isLoadingMore: Boolean = false,
)

internal class ThreadPostsRequestTracker {
    private var lastInput: GetThreadPostsInput? = null

    fun shouldLoad(input: GetThreadPostsInput): Boolean {
        if (input == lastInput) return false
        lastInput = input
        return true
    }

    fun invalidate() {
        lastInput = null
    }
}

internal class ThreadViewModel : ViewModel() {
    var state: LoadState<ThreadContent> by mutableStateOf(LoadState.Loading)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    private val requestTracker = ThreadPostsRequestTracker()
    private var loadJob: Job? = null
    private var requestGeneration = 0

    fun loadPosts(input: GetThreadPostsInput) {
        if (!requestTracker.shouldLoad(input)) return
        loadJob?.cancel()
        val generation = ++requestGeneration
        val previous = (state as? LoadState.Ready)?.value
        if (input.page == 1) {
            state = LoadState.Loading
        } else if (previous != null) {
            state = LoadState.Ready(previous.copy(isLoadingMore = true))
        }
        loadJob = viewModelScope.launch {
            try {
                state = when (val result = load { api.posts.getThreadPosts(input) }) {
                    is LoadState.Ready -> {
                        val latest = (state as? LoadState.Ready)?.value
                        LoadState.Ready(
                            ThreadContent(
                                page = result.value,
                                posts = mergeThreadPosts(
                                    existing = latest?.posts ?: previous?.posts.orEmpty(),
                                    loaded = result.value.posts,
                                    page = input.page,
                                ),
                            ),
                        )
                    }
                    is LoadState.Failed -> result
                    LoadState.Loading -> LoadState.Loading
                }
            } finally {
                if (generation == requestGeneration) isRefreshing = false
            }
        }
    }

    fun refresh() {
        isRefreshing = true
        requestGeneration++
        invalidate()
    }

    fun updatePostComments(postId: Int, comments: List<YamiboPostComment>): Boolean {
        val content = (state as? LoadState.Ready)?.value ?: return false
        val updatedPosts = replacePostComments(content.posts, postId, comments)
        if (updatedPosts === content.posts) return false
        state = LoadState.Ready(content.copy(posts = updatedPosts))
        return true
    }

    fun invalidate() {
        loadJob?.cancel()
        requestTracker.invalidate()
    }
}

internal fun mergeThreadPosts(
    existing: List<YamiboPost>,
    loaded: List<YamiboPost>,
    page: Int,
): List<YamiboPost> {
    require(page > 0) { "page must be a positive integer" }
    return if (page == 1) loaded else (existing + loaded).distinctBy { it.id }
}

internal fun replacePostComments(
    posts: List<YamiboPost>,
    postId: Int,
    comments: List<YamiboPostComment>,
): List<YamiboPost> {
    require(postId > 0) { "postId must be a positive integer" }
    val targetIndex = posts.indexOfFirst { it.id == postId }
    if (targetIndex < 0) return posts
    return posts.toMutableList().apply {
        this[targetIndex] = posts[targetIndex].copy(comments = comments)
    }
}
