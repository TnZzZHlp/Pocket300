package com.yamibo.pocket300.api

import org.json.JSONObject

data class YamiboForum(
    val id: Int,
    val name: String,
    val postCount: Int,
    val threadCount: Int,
    val todayPostCount: Int,
    val description: String,
)

data class YamiboForumCategory(val id: Int, val name: String, val forums: List<YamiboForum>)
data class YamiboForumIndex(val categories: List<YamiboForumCategory>)

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
    return YamiboForumIndex(categories)
}

private fun parseForum(value: JSONObject): YamiboForum = YamiboForum(
    id = value.positiveInt("fid", "百合会板块数据"),
    name = value.requiredString("name", "百合会板块数据"),
    postCount = value.nonNegativeInt("posts", "百合会板块数据"),
    threadCount = value.nonNegativeInt("threads", "百合会板块数据"),
    todayPostCount = value.nonNegativeInt("todayposts", "百合会板块数据"),
    description = value.stringOrNull("description").orEmpty(),
)

private fun org.json.JSONArray.objects(errorMessage: String): List<JSONObject> =
    (0 until length()).map { index -> opt(index) as? JSONObject ?: invalidResponse(errorMessage) }
