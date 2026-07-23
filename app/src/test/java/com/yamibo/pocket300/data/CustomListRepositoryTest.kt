package com.yamibo.pocket300.data

import com.yamibo.pocket300.api.YamiboSearchAuthor
import com.yamibo.pocket300.api.YamiboSearchForum
import com.yamibo.pocket300.api.YamiboSearchPage
import com.yamibo.pocket300.api.YamiboSearchPagination
import com.yamibo.pocket300.api.YamiboSearchScope
import com.yamibo.pocket300.api.YamiboSearchThread
import com.yamibo.pocket300.api.YamiboThreadSearchType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun automaticRefreshUsesConfiguredIntervalAndDefaultsToTwentyFourHours() {
        val now = 48 * HOUR_MILLIS
        val list = testList(lastSyncedAt = now - 23 * HOUR_MILLIS)

        assertFalse(list.isAutoRefreshDue(now))
        assertEquals(HOUR_MILLIS, list.millisUntilAutoRefresh(now))
        assertTrue(list.copy(lastSyncedAt = now - 24 * HOUR_MILLIS).isAutoRefreshDue(now))
        assertTrue(
            list.copy(autoRefreshIntervalHours = 1).isAutoRefreshDue(now),
        )
    }

    @Test
    fun scheduledRefreshWaitsTenSecondsBetweenDueListItems() = runBlocking {
        val now = 48 * HOUR_MILLIS
        val refreshedIds = mutableListOf<Long>()
        val intervals = mutableListOf<Long>()
        val scheduler = CustomListAutoRefreshScheduler(
            loadLists = {
                listOf(
                    testList(lastSyncedAt = 0),
                    testList(lastSyncedAt = 0).copy(id = 2),
                    testList(lastSyncedAt = now - HOUR_MILLIS).copy(id = 3),
                )
            },
            refresh = { refreshedIds += it.id },
            nowMillis = { now },
            waitForNextRefresh = { intervals += it },
        )

        scheduler.refreshDueLists()

        assertEquals(listOf(1L, 2L), refreshedIds)
        assertEquals(
            listOf(CUSTOM_LIST_AUTO_REFRESH_BETWEEN_LISTS_MILLIS),
            intervals,
        )
    }

    @Test
    fun manualRefreshUpdatesEveryListItem() = runBlocking {
        val refreshedIds = mutableListOf<Long>()
        val scheduler = CustomListAutoRefreshScheduler(
            loadLists = {
                listOf(
                    testList(lastSyncedAt = 0),
                    testList(lastSyncedAt = 0).copy(id = 2),
                    testList(lastSyncedAt = 0).copy(id = 3),
                )
            },
            refresh = { refreshedIds += it.id },
            betweenListDelayMillis = 0,
            waitForNextRefresh = {},
        )

        scheduler.refreshAllLists()

        assertEquals(listOf(1L, 2L, 3L), refreshedIds)
    }

    @Test
    fun manualRefreshContinuesAfterAListItemFails() = runBlocking {
        val attemptedIds = mutableListOf<Long>()
        val scheduler = CustomListAutoRefreshScheduler(
            loadLists = {
                listOf(
                    testList(lastSyncedAt = 0),
                    testList(lastSyncedAt = 0).copy(id = 2),
                    testList(lastSyncedAt = 0).copy(id = 3),
                )
            },
            refresh = {
                attemptedIds += it.id
                if (it.id == 2L) error("refresh failed")
            },
            betweenListDelayMillis = 0,
            waitForNextRefresh = {},
        )

        scheduler.refreshAllLists()

        assertEquals(listOf(1L, 2L, 3L), attemptedIds)
    }

    private companion object {
        const val HOUR_MILLIS = 60 * 60 * 1_000L
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
