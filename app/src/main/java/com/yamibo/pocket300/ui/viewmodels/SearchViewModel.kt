package com.yamibo.pocket300.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamibo.pocket300.api.SearchSiteThreadsInput
import com.yamibo.pocket300.api.YamiboSearchPage
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.ui.LoadState
import com.yamibo.pocket300.ui.api
import com.yamibo.pocket300.ui.load
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class SearchContent(
    val page: YamiboSearchPage,
    val threads: List<YamiboSearchThread>,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
)

internal enum class SearchQueryError { EMPTY, INVALID_USER_ID }

internal fun validateSearchQuery(
    query: String,
    type: YamiboThreadSearchType,
): SearchQueryError? = when {
    query.isBlank() -> SearchQueryError.EMPTY
    type == YamiboThreadSearchType.USER_ID && query.trim().toIntOrNull()?.takeIf { it > 0 } == null ->
        SearchQueryError.INVALID_USER_ID
    else -> null
}

internal class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var searchType by mutableStateOf(YamiboThreadSearchType.TITLE)
        private set
    var queryError: SearchQueryError? by mutableStateOf(null)
        private set
    var state: LoadState<SearchContent>? by mutableStateOf(null)
        private set

    private var submittedKeyword = ""
    private var submittedSearchType = YamiboThreadSearchType.TITLE
    private var searchId: Int? = null
    private var searchJob: Job? = null

    fun updateQuery(value: String) {
        query = value
        queryError = null
    }

    fun clearQuery() = updateQuery("")

    fun updateSearchType(value: YamiboThreadSearchType) {
        searchType = value
        queryError = null
    }

    fun submit() {
        queryError = validateSearchQuery(query, searchType)
        if (queryError != null) return
        val keyword = query.trim()
        submittedKeyword = keyword
        submittedSearchType = searchType
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
        val previous = (state as? LoadState.Ready)?.value
        state = if (replace) {
            LoadState.Loading
        } else {
            previous?.let { LoadState.Ready(it.copy(isLoadingMore = true, loadMoreError = null)) }
                ?: return
        }
        searchJob = viewModelScope.launch {
            val result = load {
                api.search.searchSiteThreads(
                    SearchSiteThreadsInput(
                        keyword = submittedKeyword,
                        page = page,
                        searchId = if (page == 1) null else searchId,
                        type = submittedSearchType,
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
                is LoadState.Failed -> if (replace) {
                    result
                } else {
                    LoadState.Ready(
                        (previous ?: return@launch).copy(
                            isLoadingMore = false,
                            loadMoreError = result.message,
                        ),
                    )
                }
                LoadState.Loading -> LoadState.Loading
            }
        }
    }
}
