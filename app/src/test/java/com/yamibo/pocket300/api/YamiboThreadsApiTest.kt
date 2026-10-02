package com.yamibo.pocket300.api

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YamiboThreadsApiTest {
    @Test
    fun parsesStickyThreadAndPagination() {
        val page = parseForumThreads(JSONObject(FIXTURE))
        assertEquals("动画", page.forum.name)
        assertTrue(page.pagination.hasNextPage)
        assertEquals("动画", page.threadTypes.single().name)
        val thread = page.threads.single()
        assertEquals(1000, thread.id)
        assertEquals("alice", thread.author.name)
        assertEquals(42, thread.author.id)
        assertEquals(2, thread.stickyLevel)
        assertEquals("动画", thread.typeName)
        assertEquals("发布时间", thread.createdAtText)
        assertEquals(4, thread.replyCount)
    }

    @Test fun toleratesMissingAnonymousAndInvalidAuthorIds() {
        listOf(null, "0", "-1", "invalid").forEach { id ->
            val fixture = JSONObject(FIXTURE)
            fixture.getJSONArray("forum_threadlist").getJSONObject(0).put("authorid", id)
            assertNull(parseForumThreads(fixture).threads.single().author.id)
        }
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsInvalidPagination() {
        val fixture = JSONObject(FIXTURE).put("page", "0")
        parseForumThreads(fixture)
    }

    @Test
    fun buildsParametersForEveryForumSort() {
        val expectedSortParameters = mapOf(
            YamiboForumThreadSort.LATEST_REPLY to mapOf(
                "filter" to "lastpost",
                "orderby" to "lastpost",
            ),
            YamiboForumThreadSort.POPULAR to mapOf("filter" to "heat", "orderby" to "heats"),
            YamiboForumThreadSort.DIGEST to mapOf(
                "filter" to "digest",
                "orderby" to "heats",
                "digest" to "1",
            ),
            YamiboForumThreadSort.NEWEST to mapOf("filter" to "dateline", "orderby" to "dateline"),
        )
        assertEquals(expectedSortParameters.keys, YamiboForumThreadSort.entries.toSet())

        expectedSortParameters.forEach { (sort, sortParameters) ->
            val parameters = buildForumThreadsParameters(
                GetForumThreadsInput(forumId = 5, page = 2, pageSize = 50, typeId = 4, sort = sort)
            )

            assertEquals("5", parameters["fid"])
            assertEquals("forumdisplay", parameters["module"])
            assertEquals("2", parameters["page"])
            assertEquals("50", parameters["tpp"])
            assertEquals("4", parameters["typeid"])
            assertEquals(sortParameters, parameters.filterKeys { it in setOf("filter", "orderby", "digest") })
        }
    }

    @Test
    fun stopsPaginationWhenFilteredPageIsEmpty() {
        val fixture = JSONObject(FIXTURE).put("forum_threadlist", JSONArray())

        val page = parseForumThreads(fixture, hasUnknownTotal = true)

        assertFalse(page.pagination.hasNextPage)
    }

    private companion object {
        val FIXTURE = """
          {
            "page":"1","tpp":"20","reward_unit":"积分",
            "forum":{"fid":"300","name":"动画","autoclose":"0","description":"","password":"0","icon":"","fup":"0","posts":"100","rules":"","threadcount":"41","picstyle":"1"},
            "group":{"groupid":"7","grouptitle":"游客"},
            "groupiconid":null,
            "threadtypes":{"types":{"1":"动画"},"icons":{"1":""}},
            "sublist":[],
            "forum_threadlist":[{
              "author":"alice","authorid":"42","dbdateline":"10","dateline":"发布时间","digest":"0","message":"摘要","attachment":"1",
              "tid":"1000","attachmentImageNumber":"2","recommend":"1","rushreply":"0","dblastpost":"11","lastpost":"回复时间","lastposter":"bob",
              "price":"0","readperm":"0","recommend_add":"3","replies":"4","replycredit":"0","special":"1","displayorder":"2","subject":"主题",
              "typeid":"1","views":"12","reply":[],
              "attachmentImagePreviewList":[[],{"attachment":"2026/07/a b.jpg","remote":"0","width":"100","height":"0","filename":"a b.jpg","filesize":"20","aid":"9"}]
            }]
          }
        """.trimIndent()
    }
}
