package com.yamibo.pocket300.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamibo.pocket300.api.SearchSiteThreadsInput
import com.yamibo.pocket300.api.YamiboSearchPage
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class SearchContent(val page: YamiboSearchPage, val threads: List<YamiboSearchThread>)

internal class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var state: LoadState<SearchContent>? by mutableStateOf(null)
        private set

    private var submittedKeyword = ""
    private var searchId: Int? = null
    private var searchJob: Job? = null

    fun updateQuery(value: String) {
        query = value
    }

    fun submit() {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            state = LoadState.Failed("请输入搜索关键字")
            return
        }
        submittedKeyword = keyword
        searchId = null
        search(page = 1, replace = true)
    }

    fun loadMore() {
        val current = (state as? LoadState.Ready)?.value ?: return
        if (!current.page.pagination.hasNextPage || searchJob?.isActive == true) return
        search(page = current.page.pagination.page + 1, replace = false)
    }

    private fun search(page: Int, replace: Boolean) {
        searchJob?.cancel()
        if (replace) state = LoadState.Loading
        val previous = (state as? LoadState.Ready)?.value
        searchJob = viewModelScope.launch {
            val result = load {
                api.search.searchSiteThreads(
                    SearchSiteThreadsInput(
                        keyword = submittedKeyword,
                        page = page,
                        searchId = if (page == 1) null else searchId,
                    ),
                )
            }
            state = when (result) {
                is LoadState.Ready -> {
                    searchId = result.value.pagination.searchId
                    LoadState.Ready(
                        SearchContent(
                            page = result.value,
                            threads = if (replace) result.value.threads
                            else (previous?.threads.orEmpty() + result.value.threads).distinctBy { it.id },
                        ),
                    )
                }
                is LoadState.Failed -> result
                LoadState.Loading -> LoadState.Loading
            }
        }
    }
}
