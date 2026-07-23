package com.yamibo.pocket300.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamibo.pocket300.api.GetThreadPostsInput
import com.yamibo.pocket300.api.YamiboPost
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

    private val requestTracker = ThreadPostsRequestTracker()
    private var loadJob: Job? = null

    fun loadPosts(input: GetThreadPostsInput) {
        if (!requestTracker.shouldLoad(input)) return
        loadJob?.cancel()
        val previous = (state as? LoadState.Ready)?.value
        if (input.page == 1) {
            state = LoadState.Loading
        } else if (previous != null) {
            state = LoadState.Ready(previous.copy(isLoadingMore = true))
        }
        loadJob = viewModelScope.launch {
            state = when (val result = load { api.posts.getThreadPosts(input) }) {
                is LoadState.Ready -> LoadState.Ready(
                    ThreadContent(
                        page = result.value,
                        posts = if (input.page == 1) result.value.posts
                        else (previous?.posts.orEmpty() + result.value.posts).distinctBy { it.id },
                    ),
                )
                is LoadState.Failed -> result
                LoadState.Loading -> LoadState.Loading
            }
        }
    }

    fun invalidate() {
        loadJob?.cancel()
        requestTracker.invalidate()
    }
}
