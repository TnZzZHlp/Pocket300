package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.SearchSiteThreadsInput
import com.yamibo.pocket300.api.YamiboSearchApi
import com.yamibo.pocket300.api.YamiboSearchErrorCode
import com.yamibo.pocket300.api.YamiboSearchException
import com.yamibo.pocket300.api.YamiboSearchPage
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CustomListSyncProgress(
    val keyword: String,
    val keywordIndex: Int,
    val keywordCount: Int,
    val page: Int,
    val totalPages: Int?,
)

enum class CustomListRefreshMode { REGULAR, FULL }

class CustomListRepository(
    private val database: CustomListDatabase,
    private val searchApi: YamiboSearchApi,
) {
    suspend fun refresh(
        list: CustomThreadList,
        mode: CustomListRefreshMode = CustomListRefreshMode.REGULAR,
        onProgress: (CustomListSyncProgress) -> Unit = {},
    ): Int = refreshMutex.withLock {
        val currentList = withContext(Dispatchers.IO) { database.getList(list.id) }
            ?: return@withLock 0
        val fetchAllPages = currentList.shouldFetchAllPages(mode)
        val results = collectCustomListThreads(
            list = currentList,
            fetchAllPages = fetchAllPages,
            search = ::searchWithRateLimit,
            onProgress = onProgress,
        )
        withContext(Dispatchers.IO) {
            if (fetchAllPages) {
                database.replaceThreads(currentList.id, results.values)
            } else {
                database.mergeThreads(currentList.id, results.values)
            }
        }
        CustomListRefreshEvents.notifyRefreshed(currentList.id)
        results.size
    }

    private suspend fun searchWithRateLimit(
        keyword: String,
        type: YamiboThreadSearchType,
        page: Int,
        searchId: Int?,
    ): YamiboSearchPage {
        var retries = 0
        while (true) {
            try {
                return searchApi.searchSiteThreads(
                    SearchSiteThreadsInput(keyword, page, searchId, type),
                )
            } catch (error: YamiboSearchException) {
                if (error.code != YamiboSearchErrorCode.RATE_LIMITED || retries >= MAX_RATE_LIMIT_RETRIES) {
                    throw error
                }
                retries++
                delay((error.retryAfterMillis ?: DEFAULT_RETRY_MILLIS) + RETRY_BUFFER_MILLIS)
            }
        }
    }

    private companion object {
        const val MAX_RATE_LIMIT_RETRIES = 3
        const val DEFAULT_RETRY_MILLIS = 10_000L
        const val RETRY_BUFFER_MILLIS = 500L
        val refreshMutex = Mutex()
    }
}

internal fun CustomThreadList.shouldFetchAllPages(mode: CustomListRefreshMode): Boolean =
    lastSyncedAt == null || mode == CustomListRefreshMode.FULL

internal suspend fun collectCustomListThreads(
    list: CustomThreadList,
    fetchAllPages: Boolean,
    search: suspend (
        keyword: String,
        type: YamiboThreadSearchType,
        page: Int,
        searchId: Int?,
    ) -> YamiboSearchPage,
    onProgress: (CustomListSyncProgress) -> Unit = {},
): LinkedHashMap<Int, YamiboSearchThread> {
    val results = linkedMapOf<Int, YamiboSearchThread>()
    list.keywords.forEachIndexed { index, keyword ->
        val first = search(keyword, list.searchType, 1, null)
        val pageCount = if (fetchAllPages) first.pagination.totalPages.coerceAtLeast(1) else 1
        onProgress(
            CustomListSyncProgress(
                keyword,
                index + 1,
                list.keywords.size,
                1,
                pageCount,
            ),
        )
        first.threads.forEach { results[it.id] = it }
        var page = 2
        var searchId = first.pagination.searchId
        while (page <= pageCount) {
            val current = search(keyword, list.searchType, page, searchId)
            searchId = current.pagination.searchId ?: searchId
            current.threads.forEach { results[it.id] = it }
            onProgress(
                CustomListSyncProgress(
                    keyword,
                    index + 1,
                    list.keywords.size,
                    page,
                    pageCount,
                ),
            )
            page++
        }
    }
    return results
}
