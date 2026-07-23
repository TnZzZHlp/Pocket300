package com.yamibo.pocket300.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamibo.pocket300.api.YamiboForumIndex
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ForumIndexViewModel : ViewModel() {
    var state: LoadState<YamiboForumIndex> by mutableStateOf(LoadState.Loading)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    private var loadJob: Job? = null
    private var refreshGeneration = 0

    init {
        load(showRefreshIndicator = false)
    }

    fun refresh(showRefreshIndicator: Boolean = true) {
        load(showRefreshIndicator)
    }

    private fun load(showRefreshIndicator: Boolean) {
        loadJob?.cancel()
        val generation = ++refreshGeneration
        if (showRefreshIndicator) isRefreshing = true
        loadJob = viewModelScope.launch {
            try {
                state = load { api.forums.getForumIndex() }
            } finally {
                if (generation == refreshGeneration) isRefreshing = false
            }
        }
    }
}

