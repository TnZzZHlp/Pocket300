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
        assertEquals("https://bbs.yamibo.com/data/attachment/forum/202607/example.jpg", post.attachments.single().url)
        assertTrue(post.attachments.single().isImage)
        assertEquals("点评", post.comments.single().message)
        assertEquals("#ff00aa", page.poll?.options?.single()?.color)
        assertEquals(25.5, page.poll?.options?.single()?.percentage ?: -1.0, 0.0)
    }

    @Test
    fun acceptsUnsignedIntMaximumPollExpiration() {
        val fixture = JSONObject(FIXTURE)
        fixture.getJSONObject("special_poll").put("expirations", "4294967295")

        assertEquals(4_294_967_295_000L, parseThreadPosts(fixture).poll?.expiresAt)
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsCommentAssignedToDifferentPost() {
        val fixture = JSONObject(FIXTURE)
        fixture.getJSONObject("comments").getJSONArray("9").getJSONObject(0).put("pid", "10")
        parseThreadPosts(fixture)
    }

    @Test
    fun fallsBackToPositionForInvalidDisplayNumber() {
        listOf("", "0", "置顶").forEach { number ->
            val fixture = JSONObject(FIXTURE)
            fixture.getJSONArray("postlist").getJSONObject(0).put("number", number)
            val post = parseThreadPosts(fixture).posts.single()
            assertEquals(1, post.number)
            assertEquals(1, post.position)
        }
    }

    @Test
    fun infersAttachmentImageWhenIsimageIsInvalid() {
        listOf("-1", "image/jpeg", "unknown").forEach { isImage ->
            val fixture = JSONObject(FIXTURE)
            fixture.getJSONArray("postlist").getJSONObject(0)
                .getJSONObject("attachments").getJSONObject("8").put("isimage", isImage)
            assertTrue(parseThreadPosts(fixture).posts.single().attachments.single().isImage)
        }
    }

    @Test
    fun removesHiddenSpamFromPostHtml() {
        val html = """正文<span style="display:none">+ v% [0 P+ _; u3 {$ y9 r</span>结尾"""

        assertEquals("正文结尾", sanitizePostHtml(html))
        assertEquals(
            "可见",
            sanitizePostHtml("""<SPAN class='noise' STYLE='color:red; display : none'>乱码</SPAN>可见"""),
        )
        assertEquals(
            "前后",
            sanitizePostHtml("""前<font class="jammer">, M0 J# x0 L/ B- S&nbsp;&nbsp;n: O</font>后"""),
        )
        assertEquals("正文", sanitizePostHtml("""<i class='foo jammer bar'>干扰</i>正文"""))
    }

    @Test
    fun parsesPostPageFromFindPostRedirect() {
        assertEquals(
            32,
            parsePostPageUrl(
                "https://bbs.yamibo.com/forum.php?mod=viewthread&tid=558130&page=32#pid41265818",
                558130,
            ),
        )
        assertEquals(
            32,
            parsePostPageUrl("https://bbs.yamibo.com/thread-558130-32-1.html#pid41265818", 558130),
        )
        assertEquals(
            null,
            parsePostPageUrl("https://bbs.yamibo.com/forum.php?mod=viewthread&tid=1&page=32", 558130),
        )
    }

    private companion object {
        val FIXTURE = """
          {
            "ppp":"20",
            "thread":{"tid":"1000","author":"alice","authorid":"42","dateline":"10","digest":"0","fid":"300","attachment":"0","heats":"1","closed":"0","lastposter":"bob","lastpost":"刚刚","maxposition":"2","price":"0","readperm":"0","recommend_add":"1","replies":"1","special":"1","subject":"投票","typeid":"0","views":"12"},
            "postlist":[{"author":"alice","authorid":"42","anonymous":"0","groupiconid":"","groupid":"10","pid":"9","dbdateline":"10","dateline":"刚刚","message":"<p>不可信正文</p>","attachment":"1","attachments":{"8":{"aid":"8","url":"data/attachment/forum/","attachment":"202607/example.jpg","filename":"example.jpg","isimage":"1"}},"first":"1","number":"1","position":"1","replycredit":"0","status":"0","tid":"1000"}],
            "comments":{"9":[{"author":"bob","authorid":"43","avatar":"//example.com/a.png","dateline":"刚刚","id":"2","comment":"点评","pid":"9","tid":"1000"}]},
            "special_poll":{"allowvote":"1","expirations":"0","maxchoices":"1","multiple":"0","visiblepoll":"1","voterscount":"4","polloptions":{"7":{"color":"ff00aa","polloptionid":"7","percent":"25.5","polloption":"选项","votes":"1"}}}
          }
        """.trimIndent()
    }
}
