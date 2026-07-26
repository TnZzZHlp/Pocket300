package com.yamibo.pocket300.data.download

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ThreadPostsSourceTest {
    @Test
    fun extractsLazyHtmlAndAttachmentImagesAcrossPostsWithStableUrlDeduplication() {
        val threadId = 1000
        val shared = "https://bbs.yamibo.com/shared.png"
        val attachment = "https://bbs.yamibo.com/attachment.png"
        val posts = listOf(
            testPost(
                threadId = threadId,
                postId = 2000,
                position = 1,
                html = """
                    <img src="placeholder.gif" data-original="$shared">
                    <img src="/static/image/smiley/default/smile.gif">
                """.trimIndent(),
                attachmentUrls = listOf(shared, attachment),
            ),
            testPost(
                threadId = threadId,
                postId = 2001,
                position = 2,
                html = "<img zoomfile='//bbs.yamibo.com/second.png' src='loading.gif'>" +
                    "<img src='$shared'>",
            ),
        )

        assertEquals(
            listOf(
                shared,
                attachment,
                "https://bbs.yamibo.com/second.png",
            ),
            threadImageUrls(posts),
        )
    }

    @Test
    fun fetchesDynamicLastPageDeduplicatesBoundaryPostsAndSortsReadingOrder() = runBlocking {
        val initialThread = testThread(replyCount = 2)
        val calls = mutableListOf<Int>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val source = ThreadPostsSource { threadId, page ->
            calls += page
            require(threadId == initialThread.id)
            when (page) {
                1 -> testPage(
                    thread = initialThread,
                    page = 1,
                    totalPages = 2,
                    posts = listOf(
                        testPost(threadId, 2000, 1),
                        testPost(threadId, 2002, 3),
                    ),
                    hasNextPage = true,
                )
                2 -> testPage(
                    thread = initialThread.copy(replyCount = 3),
                    page = 2,
                    totalPages = 3,
                    posts = listOf(
                        testPost(threadId, 2002, 3),
                        testPost(threadId, 2001, 2),
                    ),
                    totalPosts = 4,
                    hasNextPage = true,
                )
                3 -> testPage(
                    thread = initialThread.copy(replyCount = 3),
                    page = 3,
                    totalPages = 3,
                    posts = listOf(testPost(threadId, 2003, 4)),
                    totalPosts = 4,
                    hasNextPage = false,
                )
                else -> error("unexpected page")
            }
        }

        val snapshot = fetchCompleteThreadSnapshot(source, testRequest(initialThread)) {
                completed, total, _ ->
            progress += completed to total
        }

        assertEquals(listOf(1, 2, 3), calls)
        assertEquals(listOf(1, 2, 3, 4), snapshot.posts.map { it.position })
        assertEquals(3, snapshot.capturedPageCount)
        assertEquals(4, snapshot.sourceTotalPosts)
        assertEquals(listOf(1 to 2, 2 to 3, 3 to 3), progress)
    }

    @Test
    fun rejectsCrossThreadPostsAndNonterminalEmptyPages() {
        val request = testRequest()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                fetchCompleteThreadSnapshot(
                    source = ThreadPostsSource { _, _ ->
                        testPage(
                            thread = request.thread,
                            page = 1,
                            totalPages = 1,
                            posts = listOf(testPost(threadId = 9999)),
                        )
                    },
                    request = request,
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                fetchCompleteThreadSnapshot(
                    source = ThreadPostsSource { _, _ ->
                        testPage(
                            thread = request.thread,
                            page = 1,
                            totalPages = 2,
                            posts = emptyList(),
                            hasNextPage = true,
                        )
                    },
                    request = request,
                )
            }
        }
    }
}
