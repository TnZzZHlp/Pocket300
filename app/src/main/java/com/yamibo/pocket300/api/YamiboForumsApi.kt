package com.yamibo.pocket300.api

import org.json.JSONArray
import org.json.JSONObject

data class YamiboSubforum(
    val id: Int,
    val name: String,
    val postCount: Int,
    val redirectUrl: String?,
    val threadCount: Int,
    val todayPostCount: Int,
    val webUrl: String,
)

data class YamiboForum(
    val id: Int,
    val name: String,
    val postCount: Int,
    val redirectUrl: String?,
    val threadCount: Int,
    val todayPostCount: Int,
    val webUrl: String,
    val description: String,
    val iconUrl: String?,
    val subforums: List<YamiboSubforum>,
)

data class YamiboForumCategory(val id: Int, val name: String, val forums: List<YamiboForum>)
data class YamiboForumIndex(val categories: List<YamiboForumCategory>, val forums: List<YamiboForum>)

class YamiboForumsApi(private val client: YamiboClient) {
    suspend fun getForumIndex(): YamiboForumIndex {
        val response = client.requestMobileApi(mapOf("module" to "forumindex"))
        val serverCode = response.message?.code?.takeIf(String::isNotBlank) ?: response.error
        if (serverCode != null) {
            throw YamiboApiException(
                YamiboApiErrorCode.SERVER_ERROR,
                response.message?.message?.takeIf(String::isNotBlank) ?: "百合会板块服务返回了错误",
                serverCode,
            )
        }
        return parseForumIndex(response.variables ?: invalidResponse("百合会未返回板块首页数据"))
    }
}

fun parseForumIndex(variables: JSONObject): YamiboForumIndex {
    val forumList = variables.arrayOrNull("forumlist") ?: invalidResponse("百合会未返回板块首页数据")
    val categoryList = variables.arrayOrNull("catlist") ?: invalidResponse("百合会未返回板块首页数据")
    val forums = forumList.objects("百合会返回了无效的板块数据").map(::parseForum)
    val byId = forums.associateBy(YamiboForum::id)
    val categories = categoryList.objects("百合会返回了无效的板块分类数据").map { category ->
        val ids = category.arrayOrNull("forums") ?: invalidResponse("百合会返回了无效的板块分类数据")
        YamiboForumCategory(
            id = category.positiveInt("fid", "百合会板块分类数据"),
            name = category.requiredString("name", "百合会板块分类数据"),
            forums = (0 until ids.length()).map { index ->
                ids.optString(index, "").toIntOrNull()
                    ?.takeIf { it > 0 }
                    ?: invalidResponse("百合会板块分类包含无效的板块 ID")
            }.mapNotNull(byId::get),
        )
    }
    return YamiboForumIndex(categories, forums)
}

private fun parseForum(value: JSONObject): YamiboForum {
    val parsed = parseSubforum(value)
    val subforums = when (val raw = value.opt("sublist")) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> raw.objects("百合会返回了无效的子板块数据").map(::parseSubforum)
        else -> invalidResponse("百合会返回了无效的子板块列表")
    }
    return YamiboForum(
        id = parsed.id,
        name = parsed.name,
        postCount = parsed.postCount,
        redirectUrl = parsed.redirectUrl,
        threadCount = parsed.threadCount,
        todayPostCount = parsed.todayPostCount,
        webUrl = parsed.webUrl,
        description = value.stringOrNull("description").orEmpty(),
        iconUrl = value.nonBlankStringOrNull("icon"),
        subforums = subforums,
    )
}

private fun parseSubforum(value: JSONObject): YamiboSubforum {
    val context = "百合会板块数据"
    val id = value.positiveInt("fid", context)
    val redirectUrl = value.nonBlankStringOrNull("redirect")
    return YamiboSubforum(
        id = id,
        name = value.requiredString("name", context),
        postCount = value.nonNegativeInt("posts", context),
        redirectUrl = redirectUrl,
        threadCount = value.nonNegativeInt("threads", context),
        todayPostCount = value.nonNegativeInt("todayposts", context),
        webUrl = redirectUrl ?: "$YAMIBO_ORIGIN/forum.php?mod=forumdisplay&fid=$id",
    )
}

private fun JSONArray.objects(errorMessage: String): List<JSONObject> =
    (0 until length()).map { index -> opt(index) as? JSONObject ?: invalidResponse(errorMessage) }
