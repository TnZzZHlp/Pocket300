package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.YamiboSearchAuthor
import com.yamibo.pocket300.api.YamiboSearchForum
import com.yamibo.pocket300.api.YamiboSearchPage
import com.yamibo.pocket300.api.YamiboSearchPagination
import com.yamibo.pocket300.api.YamiboSearchScope
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomListRepositoryTest {
    @Test
    fun firstRefreshLoadsEverySearchResultPage() = runBlocking {
        val list = testList(lastSyncedAt = null)
        val requestedPages = mutableListOf<Int>()

        val threads = collectCustomListThreads(
            list = list,
            fetchAllPages = list.shouldFetchAllPages(CustomListRefreshMode.REGULAR),
            search = { _, _, page, _ ->
                requestedPages += page
                searchPage(page, totalPages = 3)
            },
        )

        assertEquals(listOf(1, 2, 3), requestedPages)
        assertEquals(listOf(1, 2, 3), threads.keys.toList())
    }

    @Test
    fun routineRefreshLoadsOnlyTheFirstSearchResultPage() = runBlocking {
        val list = testList(lastSyncedAt = 1L)
        val requestedPages = mutableListOf<Int>()
        val progressPages = mutableListOf<Int?>()

        collectCustomListThreads(
            list = list,
            fetchAllPages = list.shouldFetchAllPages(CustomListRefreshMode.REGULAR),
            search = { _, _, page, _ ->
                requestedPages += page
                searchPage(page, totalPages = 3)
            },
            onProgress = { progressPages += it.totalPages },
        )

        assertEquals(listOf(1), requestedPages)
        assertEquals(listOf(1), progressPages)
    }

    @Test
    fun explicitFullRefreshLoadsEverySearchResultPage() = runBlocking {
        val list = testList(lastSyncedAt = 1L)
        val requestedPages = mutableListOf<Int>()

        collectCustomListThreads(
            list = list,
            fetchAllPages = list.shouldFetchAllPages(CustomListRefreshMode.FULL),
            search = { _, _, page, _ ->
                requestedPages += page
                searchPage(page, totalPages = 2)
            },
        )

        assertEquals(listOf(1, 2), requestedPages)
    }

    @Test
    fun automaticRefreshWaitsTenSecondsBetweenListItems() = runBlocking {
        val refreshedIds = mutableListOf<Long>()
        val intervals = mutableListOf<Long>()
        val scheduler = CustomListAutoRefreshScheduler(
            loadLists = { listOf(testList(1L), testList(1L).copy(id = 2)) },
            refresh = { refreshedIds += it.id },
            waitForNextRefresh = { interval ->
                intervals += interval
                if (intervals.size == 2) throw CancellationException()
            },
        )

        try {
            scheduler.refreshContinuously()
        } catch (_: CancellationException) {
            // The injected wait ends the otherwise continuous foreground loop.
        }

        assertEquals(listOf(1L, 2L), refreshedIds)
        assertEquals(
            listOf(CUSTOM_LIST_AUTO_REFRESH_INTERVAL_MILLIS, CUSTOM_LIST_AUTO_REFRESH_INTERVAL_MILLIS),
            intervals,
        )
    }

    private fun testList(lastSyncedAt: Long?) = CustomThreadList(
        id = 1,
        name = "Test",
        keywords = listOf("keyword"),
        searchType = YamiboThreadSearchType.TITLE,
        createdAt = 0,
        updatedAt = 0,
        lastSyncedAt = lastSyncedAt,
        threadCount = 0,
        excludedCount = 0,
    )

    private fun searchPage(page: Int, totalPages: Int) = YamiboSearchPage(
        forumId = null,
        keyword = "keyword",
        pagination = YamiboSearchPagination(
            hasNextPage = page < totalPages,
            page = page,
            pageSize = 20,
            searchId = 100,
            totalPages = totalPages,
            totalThreads = totalPages * 20,
        ),
        scope = YamiboSearchScope.SITE,
        threads = listOf(
            YamiboSearchThread(
                author = YamiboSearchAuthor(null, 1, "Author"),
                createdAtText = "Today",
                excerpt = null,
                forum = YamiboSearchForum(300, "Forum", "https://example.com/forum"),
                id = page,
                imageUrls = emptyList(),
                replyCount = 0,
                subject = "Thread $page",
                viewCount = 0,
                webUrl = "https://example.com/thread/$page",
            ),
        ),
    )
}
