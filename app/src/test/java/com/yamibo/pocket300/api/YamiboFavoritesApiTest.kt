package com.yamibo.pocket300.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class YamiboFavoritesApiTest {
    @Test fun requestsFavoriteThreadListModule() {
        assertEquals(
            mapOf("module" to "myfavthread", "page" to "2"),
            favoriteThreadListParameters(2),
        )
    }

    @Test fun buildsRemoveFavoriteParameters() {
        assertEquals(
            mapOf(
                "ac" to "favorite",
                "favid" to "9",
                "mobile" to "2",
                "mod" to "spacecp",
                "op" to "delete",
                "type" to "thread",
            ),
            removeFavoriteParameters(9),
        )
        assertEquals(
            mapOf("deletesubmit" to "1", "formhash" to "hash"),
            removeFavoriteForm("hash"),
        )
    }

    @Test fun parsesFavoriteThreads() {
        val favorites = parseFavoriteThreads(
            JSONObject(
                """{"list":[{"favid":"9","id":"493657","title":"测试主题","description":"摘要","dateline":"昨天"}]}""",
            ),
        )
        assertEquals(
            YamiboFavoriteThread(9, 493657, "测试主题", "摘要", "昨天"),
            favorites.single(),
        )
    }

    @Test fun parsesEmptyFavorites() {
        assertEquals(emptyList<YamiboFavoriteThread>(), parseFavoriteThreads(JSONObject("""{"list":null}""")))
    }
}
