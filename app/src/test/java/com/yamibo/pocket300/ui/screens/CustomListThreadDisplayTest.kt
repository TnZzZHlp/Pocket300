package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.data.CustomListThread
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomListThreadDisplayTest {
    private val today = LocalDate.of(2026, 7, 15)
    private val threads = listOf(
        thread(200, "今天"),
        thread(300, "2025-12-31"),
        thread(100, "昨天"),
    )

    @Test
    fun keepsOnlyReadThreads() {
        assertEquals(
            listOf(200, 300),
            display(ThreadReadFilter.READ, ThreadPublicationOrder.NEWEST_FIRST, setOf(200, 300)),
        )
    }

    @Test
    fun keepsOnlyUnreadThreads() {
        assertEquals(
            listOf(100),
            display(ThreadReadFilter.UNREAD, ThreadPublicationOrder.NEWEST_FIRST, setOf(200, 300)),
        )
    }

    @Test
    fun sortsByPublicationTimeDescending() {
        assertEquals(
            listOf(200, 100, 300),
            display(ThreadReadFilter.ALL, ThreadPublicationOrder.NEWEST_FIRST),
        )
    }

    @Test
    fun sortsByPublicationTimeAscending() {
        assertEquals(
            listOf(300, 100, 200),
            display(ThreadReadFilter.ALL, ThreadPublicationOrder.OLDEST_FIRST),
        )
    }

    @Test
    fun understandsMonthDayDatesFromTheCurrentAndPreviousYear() {
        val dated = listOf(thread(20, "12 月 31 日"), thread(10, "7 月 14 日"))
        assertEquals(
            listOf(20, 10),
            filterAndSortCustomListThreads(
                dated,
                emptySet(),
                ThreadReadFilter.ALL,
                ThreadPublicationOrder.OLDEST_FIRST,
                today,
            ).map(CustomListThread::threadId),
        )
    }

    private fun display(
        filter: ThreadReadFilter,
        order: ThreadPublicationOrder,
        readIds: Set<Int> = emptySet(),
    ) = filterAndSortCustomListThreads(threads, readIds, filter, order, today)
        .map(CustomListThread::threadId)

    private fun thread(id: Int, createdAt: String) = CustomListThread(
        listId = 1,
        threadId = id,
        forumId = 300,
        forumName = "测试",
        subject = "主题 $id",
        authorName = "作者",
        createdAtText = createdAt,
        excerpt = null,
        replyCount = 0,
        viewCount = 0,
        webUrl = "https://example.com/$id",
    )
}
