package com.yamibo.pocket300.data.download

import com.yamibo.pocket300.api.YamiboThreadSpecialType
import com.yamibo.pocket300.data.CustomListThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadDownloadModelsTest {
    @Test
    fun createsProvisionalRequestFromCustomListMetadata() {
        val request = ThreadDownloadRequest.create(
            thread = customListThread(),
            requestedAt = 123L,
        )

        assertEquals(ThreadDownloadKey(100), request.key)
        assertEquals(123L, request.requestedAt)
        assertEquals("列表主题", request.thread.subject)
        assertEquals(300, request.thread.forumId)
        assertEquals(12, request.thread.replyCount)
        assertEquals(34, request.thread.viewCount)
        assertEquals("列表作者", request.thread.author.name)
        assertNull(request.thread.author.id)
        assertFalse(request.thread.author.isAnonymous)
        assertEquals(YamiboThreadSpecialType.NORMAL, request.thread.specialType)
        assertEquals("https://bbs.yamibo.com/thread-100-1-1.html", request.referer)
    }

    @Test
    fun fallsBackToCanonicalRefererForInvalidListUrl() {
        val request = ThreadDownloadRequest.create(
            thread = customListThread(webUrl = "not a URL"),
        )

        assertEquals("https://bbs.yamibo.com/thread-100-1-1.html", request.thread.webUrl)
        assertEquals(request.thread.webUrl, request.referer)
    }

    private fun customListThread(
        webUrl: String = "https://bbs.yamibo.com/thread-100-1-1.html",
    ) = CustomListThread(
        listId = 1L,
        threadId = 100,
        forumId = 300,
        forumName = "测试分区",
        subject = "列表主题",
        authorName = "列表作者",
        createdAtText = "昨天",
        excerpt = "摘要",
        replyCount = 12,
        viewCount = 34,
        webUrl = webUrl,
    )
}
