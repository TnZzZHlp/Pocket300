package com.yamibo.pocket300.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamiboMessagesApiTest {
    @Test
    fun parsesPrivateThread() {
        val page = parseMessageThreads(JSONObject(THREAD_FIXTURE))
        assertEquals(1, page.pagination.totalItems)
        val thread = page.threads.single()
        assertEquals(YamiboMessageThreadKind.PRIVATE, thread.kind)
        assertTrue(thread.isUnread)
        assertEquals("Alice", thread.partner?.name)
        assertEquals("你好 & 欢迎", thread.lastMessage.text)
    }

    @Test
    fun parsesConversationAndViewerDirection() {
        val page = parsePrivateConversation(JSONObject(CONVERSATION_FIXTURE), 42)
        assertEquals("Alice", page.partner.name)
        assertTrue(page.messages.single().isFromViewer)
        assertEquals("Bob", page.messages.single().author.name)
    }

    @Test(expected = YamiboMessageException::class)
    fun rejectsUnrelatedConversation() {
        parsePrivateConversation(JSONObject(CONVERSATION_FIXTURE), 99)
    }

    private companion object {
        val THREAD_FIXTURE = """
          {"member_uid":"7","member_username":"Bob","count":"1","perpage":"15","page":"1","list":[
            {"plid":"9","touid":"42","tousername":"Alice","msgfromid":"7","msgfrom":"Bob","dateline":"10","vdateline":"刚刚","isnew":"1","message":"你好 &amp; <b>欢迎</b>","numbers":"0","subject":""}
          ]}
        """.trimIndent()
        val CONVERSATION_FIXTURE = """
          {"member_uid":"7","member_username":"Bob","count":"1","perpage":"5","page":"1","list":[
            {"pmid":"3","msgfromid":"7","msgfrom":"Bob","touid":"42","tousername":"Alice","dateline":"10","vdateline":"刚刚","message":"hi"}
          ]}
        """.trimIndent()
    }
}
