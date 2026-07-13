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
        assertEquals(
            "https://bbs.yamibo.com/uc_server/avatar.php?uid=42&size=small",
            post.author.avatarUrl,
        )
        assertEquals(
            "https://example.com/a.png",
            post.comments.single().author.avatarUrl,
        )
        assertTrue(page.thread.hasRatings)
        assertEquals(4, post.ratingCount)
        assertEquals("<p>不可信正文</p>", post.html)
        assertEquals("https://bbs.yamibo.com/data/attachment/forum/202607/example.jpg", post.attachments.single().url)
        assertTrue(post.attachments.single().isImage)
        assertEquals("点评", post.comments.single().message)
        assertEquals("#ff00aa", page.poll?.options?.single()?.color)
        assertEquals(25.5, page.poll?.options?.single()?.percentage ?: -1.0, 0.0)
    }

    @Test
    fun addsAuthorIdWhenRequestingOnlyOriginalPoster() {
        assertEquals(
            mapOf("module" to "viewthread", "page" to "2", "tid" to "1000", "authorid" to "42"),
            threadPostsParameters(GetThreadPostsInput(threadId = 1000, page = 2, authorId = 42)),
        )
        assertFalse(threadPostsParameters(GetThreadPostsInput(1000)).containsKey("authorid"))
    }

    @Test
    fun keepsOnlyRequestedAuthorAndUsesFilteredPageSizeForPagination() {
        val fixture = JSONObject(FIXTURE)
        fixture.put("ppp", "1")
        fixture.getJSONArray("postlist").put(
            JSONObject(fixture.getJSONArray("postlist").getJSONObject(0).toString())
                .put("pid", "10")
                .put("author", "bob")
                .put("authorid", "43")
                .put("first", "0")
                .put("number", "2")
                .put("position", "2"),
        )

        val page = parseThreadPosts(fixture, authorId = 42)

        assertEquals(listOf(42), page.posts.map { it.author.id })
        assertTrue(page.pagination.hasNextPage)

        fixture.put("ppp", "20")
        assertFalse(parseThreadPosts(fixture, authorId = 42).pagination.hasNextPage)
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

    @Test
    fun parsesCompletePostRatingRows() {
        val html = """
            <table class="list">
              <thead><tr><td>积分</td><td>用户名</td><td>时间</td><td>理由</td></tr></thead>
              <tr>
                <td>百合币 +3 枚</td>
                <td><a href="space-uid-42.html">Alice &amp; Bob</a></td>
                <td>2026-7-13 19:25</td>
                <td>生日快乐 &amp; 好图</td>
              </tr>
              <tr>
                <td>贡献 -1 点</td>
                <td><a href="home.php?mod=space&amp;uid=43">Carol</a></td>
                <td>2026-7-13 19:30</td>
                <td></td>
              </tr>
            </table>
        """.trimIndent()

        val ratings = parsePostRatings(html)

        assertEquals(2, ratings.size)
        assertEquals("百合币", ratings[0].creditName)
        assertEquals(3, ratings[0].score)
        assertEquals("枚", ratings[0].unit)
        assertEquals(42, ratings[0].userId)
        assertEquals("Alice & Bob", ratings[0].username)
        assertEquals("生日快乐 & 好图", ratings[0].reason)
        assertEquals(-1, ratings[1].score)
        assertEquals("", ratings[1].reason)
    }

    @Test
    fun findsEveryRatedPostFromThreadPage() {
        val html = """
            <dl id="ratelog_41291769" class="rate"></dl>
            <div id="post_rate_div_41291771"></div>
            <DL class='rate' ID='ratelog_41291772'></DL>
            <dl id="ratelog_invalid"></dl>
        """.trimIndent()

        assertEquals(setOf(41291769, 41291772), parseRatedPostIds(html))
    }

    private companion object {
        val FIXTURE = """
          {
            "ppp":"20",
            "thread":{"tid":"1000","author":"alice","authorid":"42","dateline":"10","digest":"0","fid":"300","attachment":"0","heats":"1","rate":"1","closed":"0","lastposter":"bob","lastpost":"刚刚","maxposition":"2","price":"0","readperm":"0","recommend_add":"1","replies":"1","special":"1","subject":"投票","typeid":"0","views":"12"},
            "postlist":[{"author":"alice","authorid":"42","anonymous":"0","groupiconid":"","groupid":"10","pid":"9","dbdateline":"10","dateline":"刚刚","message":"<p>不可信正文</p>","attachment":"1","attachments":{"8":{"aid":"8","url":"data/attachment/forum/","attachment":"202607/example.jpg","filename":"example.jpg","isimage":"1"}},"first":"1","number":"1","position":"1","ratetimes":"4","replycredit":"0","status":"0","tid":"1000"}],
            "comments":{"9":[{"author":"bob","authorid":"43","avatar":"//example.com/a.png","dateline":"刚刚","id":"2","comment":"点评","pid":"9","tid":"1000"}]},
            "special_poll":{"allowvote":"1","expirations":"0","maxchoices":"1","multiple":"0","visiblepoll":"1","voterscount":"4","polloptions":{"7":{"color":"ff00aa","polloptionid":"7","percent":"25.5","polloption":"选项","votes":"1"}}}
          }
        """.trimIndent()
    }
}
