package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.SearchSiteThreadsInput
import com.yamibo.pocket300.api.YamiboSearchApi
import com.yamibo.pocket300.api.YamiboSearchErrorCode
import com.yamibo.pocket300.api.YamiboSearchException
import com.yamibo.pocket300.api.YamiboSearchPage
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType
import com.yamibo.pocket300.logging.AppLogger
import kotlinx.coroutines.CancellationException
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
    private val onNewThreadsForAutoDownload: suspend (
        list: CustomThreadList,
        threads: List<CustomListThread>,
    ) -> Unit = { _, _ -> },
) {
    suspend fun refresh(
        list: CustomThreadList,
        mode: CustomListRefreshMode = CustomListRefreshMode.REGULAR,
        onProgress: (CustomListSyncProgress) -> Unit = {},
    ): Int = refreshMutex.withLock {
        val currentList = withContext(Dispatchers.IO) { database.getList(list.id) }
        if (currentList == null) {
            AppLogger.debug(TAG) { "Custom list ${list.id} disappeared before refresh started" }
            return@withLock 0
        }
        val fetchAllPages = currentList.shouldFetchAllPages(mode)
        AppLogger.info(TAG) {
            "Refreshing custom list ${currentList.id}; mode=$mode, fetchAllPages=$fetchAllPages"
        }
        val results = collectCustomListThreads(
            list = currentList,
            fetchAllPages = fetchAllPages,
            search = ::searchWithRateLimit,
            onProgress = onProgress,
        )
        val addedThreads = withContext(Dispatchers.IO) {
            if (fetchAllPages) {
                database.replaceThreads(currentList.id, results.values)
            } else {
                database.mergeThreads(currentList.id, results.values)
            }
        }
        if (currentList.shouldAutoDownloadAddedThreads(addedThreads.size)) {
            try {
                onNewThreadsForAutoDownload(currentList, addedThreads)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.error(TAG, error) {
                    "Could not enqueue ${addedThreads.size} new threads from custom list ${currentList.id}"
                }
            }
        }
        CustomListRefreshEvents.notifyRefreshed(currentList.id)
        AppLogger.info(TAG) {
            "Refreshed custom list ${currentList.id}; collectedThreads=${results.size}"
        }
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
                val retryDelay = (error.retryAfterMillis ?: DEFAULT_RETRY_MILLIS) + RETRY_BUFFER_MILLIS
                AppLogger.warn(TAG) {
                    "Custom list search was rate limited; retry=$retries/$MAX_RATE_LIMIT_RETRIES, " +
                        "delayMillis=$retryDelay"
                }
                delay(retryDelay)
            }
        }
    }

    private companion object {
        const val MAX_RATE_LIMIT_RETRIES = 3
        const val DEFAULT_RETRY_MILLIS = 10_000L
        const val RETRY_BUFFER_MILLIS = 500L
        const val TAG = "CustomListRepository"
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
