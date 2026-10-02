package com.yamibo.pocket300.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun parsesOptionalAuthorIdentityAndReplies() {
        val favorite = parseFavoriteThreads(JSONObject(
            """{"list":[{"favid":"9","id":"100","title":"主题","author":"alice","authorid":"42","replies":"128"}]}""",
        )).single()
        assertEquals(42, favorite.author!!.id)
        assertEquals(yamiboAvatarUrl(42), favorite.author.avatarUrl)
        assertEquals(128, favorite.replyCount)
    }

    @Test fun neverTreatsFavoriteOwnerUidAsThreadAuthorId() {
        val favorite = parseFavoriteThreads(JSONObject(
            """{"list":[{"favid":"9","id":"100","title":"主题","uid":"99","author":"alice","replies":"invalid"}]}""",
        )).single()
        assertEquals("alice", favorite.author!!.name)
        assertNull(favorite.author.id)
        assertNull(favorite.author.avatarUrl)
        assertNull(favorite.replyCount)
    }

    @Test fun toleratesInvalidOptionalIdentityAndNegativeReplyCount() {
        val favorite = parseFavoriteThreads(JSONObject(
            """{"list":[{"favid":"9","id":"100","title":"主题","author":"匿名","authorid":"0","replies":"-1"}]}""",
        )).single()
        assertNull(favorite.author!!.id)
        assertNull(favorite.replyCount)
    }

    @Test fun parsesEmptyFavorites() {
        assertEquals(emptyList<YamiboFavoriteThread>(), parseFavoriteThreads(JSONObject("""{"list":null}""")))
    }
}
