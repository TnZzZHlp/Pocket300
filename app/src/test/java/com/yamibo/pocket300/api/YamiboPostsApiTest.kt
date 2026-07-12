package com.yamibo.pocket300.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YamiboPostsApiTest {
    @Test
    fun parsesPostsCommentsAndPoll() {
        val page = parseThreadPosts(JSONObject(FIXTURE), 1)
        assertEquals(1000, page.thread.id)
        assertEquals(YamiboThreadSpecialType.POLL, page.thread.specialType)
        assertEquals(2, page.pagination.totalPosts)
        assertEquals(1, page.pagination.totalPages)
        assertFalse(page.pagination.hasNextPage)
        val post = page.posts.single()
        assertTrue(post.isOriginalPost)
        assertEquals("<p>不可信正文</p>", post.html)
        assertEquals("点评", post.comments.single().message)
        assertEquals("#ff00aa", page.poll?.options?.single()?.color)
        assertEquals(25.5, page.poll?.options?.single()?.percentage ?: -1.0, 0.0)
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsCommentAssignedToDifferentPost() {
        val fixture = JSONObject(FIXTURE)
        fixture.getJSONObject("comments").getJSONArray("9").getJSONObject(0).put("pid", "10")
        parseThreadPosts(fixture)
    }

    private companion object {
        val FIXTURE = """
          {
            "ppp":"20",
            "thread":{"tid":"1000","author":"alice","authorid":"42","dateline":"10","digest":"0","fid":"300","attachment":"0","heats":"1","closed":"0","lastposter":"bob","lastpost":"刚刚","maxposition":"2","price":"0","readperm":"0","recommend_add":"1","replies":"1","special":"1","subject":"投票","typeid":"0","views":"12"},
            "postlist":[{"author":"alice","authorid":"42","anonymous":"0","groupiconid":"","groupid":"10","pid":"9","dbdateline":"10","dateline":"刚刚","message":"<p>不可信正文</p>","attachment":"0","first":"1","number":"1","position":"1","replycredit":"0","status":"0","tid":"1000"}],
            "comments":{"9":[{"author":"bob","authorid":"43","avatar":"//example.com/a.png","dateline":"刚刚","id":"2","comment":"点评","pid":"9","tid":"1000"}]},
            "special_poll":{"allowvote":"1","expirations":"0","maxchoices":"1","multiple":"0","visiblepoll":"1","voterscount":"4","polloptions":{"7":{"color":"ff00aa","polloptionid":"7","percent":"25.5","polloption":"选项","votes":"1"}}}
          }
        """.trimIndent()
    }
}
