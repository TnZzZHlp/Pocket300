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
import kotlinx.coroutines.withContext

data class CustomListSyncProgress(
    val keyword: String,
    val keywordIndex: Int,
    val keywordCount: Int,
    val page: Int,
    val totalPages: Int?,
)

class CustomListRepository(
    private val database: CustomListDatabase,
    private val searchApi: YamiboSearchApi,
) {
    suspend fun refresh(
        list: CustomThreadList,
        onProgress: (CustomListSyncProgress) -> Unit = {},
    ): Int {
        val results = linkedMapOf<Int, YamiboSearchThread>()
        list.keywords.forEachIndexed { index, keyword ->
            val first = searchWithRateLimit(keyword, list.searchType, 1, null)
            onProgress(
                CustomListSyncProgress(
                    keyword,
                    index + 1,
                    list.keywords.size,
                    1,
                    first.pagination.totalPages,
                ),
            )
            first.threads.forEach { results[it.id] = it }
            var page = 2
            var searchId = first.pagination.searchId
            while (page <= first.pagination.totalPages) {
                val current = searchWithRateLimit(keyword, list.searchType, page, searchId)
                searchId = current.pagination.searchId ?: searchId
                current.threads.forEach { results[it.id] = it }
                onProgress(
                    CustomListSyncProgress(
                        keyword,
                        index + 1,
                        list.keywords.size,
                        page,
                        first.pagination.totalPages,
                    ),
                )
                page++
            }
        }
        withContext(Dispatchers.IO) { database.replaceThreads(list.id, results.values) }
        return results.size
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
    }
}
