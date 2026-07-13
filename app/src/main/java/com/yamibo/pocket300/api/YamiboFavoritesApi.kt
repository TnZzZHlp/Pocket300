package com.yamibo.pocket300.api

import org.json.JSONArray
import org.json.JSONObject

data class YamiboFavoriteThread(
    val favoriteId: Int,
    val threadId: Int,
    val title: String,
    val description: String,
    val createdAtText: String,
)

class YamiboFavoritesApi(private val client: YamiboClient) {
    suspend fun getFavoriteThreads(page: Int = 1): List<YamiboFavoriteThread> {
        require(page > 0) { "page must be a positive integer" }
        val response = client.requestMobileApi(
            mapOf("module" to "favthread", "page" to page.toString()),
        )
        response.message?.let { message ->
            val loginRequired = message.code.startsWith("to_login") ||
                message.code.startsWith("login_before_enter_home")
            throw YamiboApiException(
                YamiboApiErrorCode.SERVER_ERROR,
                if (loginRequired) "请先登录百合会" else message.message.ifBlank { "读取收藏失败" },
                message.code,
            )
        }
        return parseFavoriteThreads(response.variables ?: invalidResponse("百合会未返回收藏数据"))
    }

    suspend fun addThread(threadId: Int) {
        require(threadId > 0) { "threadId must be a positive integer" }
        val session = YamiboAuthApi(client).getCurrentSession()
            ?: throw YamiboApiException(YamiboApiErrorCode.SERVER_ERROR, "请先登录百合会", "not_authenticated")
        if (session.formHash.isBlank()) invalidResponse("百合会未返回收藏所需的校验值")
        client.requestPage(
            "/home.php",
            mapOf(
                "ac" to "favorite",
                "formhash" to session.formHash,
                "id" to threadId.toString(),
                "mobile" to "2",
                "mod" to "spacecp",
                "type" to "thread",
            ),
        )
    }
}

internal fun parseFavoriteThreads(variables: JSONObject): List<YamiboFavoriteThread> {
    val raw = variables.opt("list")
    if (raw == null || raw == JSONObject.NULL) return emptyList()
    val list = raw as? JSONArray ?: invalidResponse("百合会返回了无效的收藏数据")
    return (0 until list.length()).map { index ->
        val value = list.opt(index) as? JSONObject ?: invalidResponse("百合会返回了无效的收藏条目")
        YamiboFavoriteThread(
            favoriteId = value.positiveFavoriteInt("favid"),
            threadId = value.positiveFavoriteInt("id"),
            title = value.favoriteString("title"),
            description = value.favoriteString("description", ""),
            createdAtText = value.favoriteString("dateline", ""),
        )
    }
}

private fun JSONObject.positiveFavoriteInt(key: String): Int =
    opt(key)?.toString()?.toIntOrNull()?.takeIf { it > 0 }
        ?: invalidResponse("百合会收藏条目包含无效的 $key 字段")

private fun JSONObject.favoriteString(key: String, fallback: String? = null): String {
    val value = opt(key).takeUnless { it == null || it == JSONObject.NULL } as? String
    return value ?: fallback ?: invalidResponse("百合会收藏条目缺少 $key 字段")
}
