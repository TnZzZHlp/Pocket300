package com.yamibo.pocket300.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YamiboForumsApiTest {
    @Test
    fun parsesForumIndexAndSkipsInvisibleCategoryIds() {
        val result = parseForumIndex(JSONObject(FIXTURE))

        assertEquals(1, result.forums.size)
        assertEquals("动画", result.forums.single().name)
        assertEquals(12, result.forums.single().todayPostCount)
        assertEquals(1, result.forums.single().subforums.size)
        assertNull(result.forums.single().redirectUrl)
        assertEquals(listOf(300), result.categories.single().forums.map(YamiboForum::id))
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsArrayWhereForumObjectIsRequired() {
        parseForumIndex(JSONObject("""{"forumlist":[[]],"catlist":[]}"""))
    }

    private companion object {
        val FIXTURE = """
            {
              "forumlist": [{
                "fid":"300","name":"动画","posts":"99","threads":"20","todayposts":"12",
                "redirect":"","description":"动画讨论","icon":"",
                "sublist":[{"fid":"301","name":"新番","posts":"3","threads":"2","todayposts":"1","redirect":""}]
              }],
              "catlist":[{"fid":"1","name":"江湖","forums":["300","999"]}]
            }
        """.trimIndent()
    }
}
