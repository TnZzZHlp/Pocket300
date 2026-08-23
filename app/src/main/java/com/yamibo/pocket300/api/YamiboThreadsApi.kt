package com.yamibo.pocket300.api

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

data class GetForumThreadsInput(
    val forumId: Int,
    val page: Int = 1,
    val pageSize: Int = 20,
    val typeId: Int? = null,
    val sort: YamiboForumThreadSort = YamiboForumThreadSort.LATEST_REPLY,
)

enum class YamiboForumThreadSort(
    internal val filter: String,
    internal val orderBy: String? = null,
    internal val digest: Int? = null,
) {
    LATEST_REPLY("lastpost", "lastpost"),
    POPULAR("heat", "heats"),
    DIGEST("digest", "heats", digest = 1),
    NEWEST("dateline", "dateline"),
}

data class YamiboForumDetails(val name: String, val threadCount: Int)
data class YamiboForumChild(val id: Int, val name: String, val threadCount: Int)
data class YamiboThreadType(val id: Int, val name: String)
data class YamiboThreadAuthor(val name: String)

data class YamiboThread(
    val author: YamiboThreadAuthor,
    val createdAtText: String,
    val id: Int,
    val replyCount: Int,
    val stickyLevel: Int,
    val subject: String,
    val typeName: String?,
)

data class YamiboForumPagination(val hasNextPage: Boolean, val page: Int)

data class YamiboForumThreadsPage(
    val forum: YamiboForumDetails,
    val pagination: YamiboForumPagination,
    val subforums: List<YamiboForumChild>,
    val threadTypes: List<YamiboThreadType>,
    val threads: List<YamiboThread>,
)

class YamiboThreadsApi(private val client: YamiboClient) {
    suspend fun getForumThreads(input: GetForumThreadsInput): YamiboForumThreadsPage {
        val response = client.requestMobileApi(buildForumThreadsParameters(input))
        val serverCode = response.message?.code?.takeIf(String::isNotBlank) ?: response.error
        if (serverCode != null) {
            val message = if (serverCode == "forum_nonexistence") {
                "板块不存在或当前账号无权访问"
            } else {
                response.message?.message?.takeIf(String::isNotBlank) ?: "百合会帖子服务返回了错误"
            }
            throw YamiboApiException(YamiboApiErrorCode.SERVER_ERROR, message, serverCode)
        }
        return parseForumThreads(
            response.variables ?: invalidResponse("百合会未返回帖子列表数据"),
            hasUnknownTotal = input.typeId != null ||
                input.sort != YamiboForumThreadSort.LATEST_REPLY,
        )
    }
}

internal fun buildForumThreadsParameters(input: GetForumThreadsInput): Map<String, String> {
    require(input.forumId > 0) { "forumId must be a positive integer" }
    require(input.page > 0) { "page must be a positive integer" }
    require(input.pageSize > 0) { "pageSize must be a positive integer" }
    require(input.pageSize <= 100) { "pageSize must not exceed 100" }
    input.typeId?.let { require(it > 0) { "typeId must be a positive integer" } }

    return buildMap {
        put("fid", input.forumId.toString())
        put("module", "forumdisplay")
        put("page", input.page.toString())
        put("tpp", input.pageSize.toString())
        put("filter", input.sort.filter)
        input.sort.orderBy?.let { put("orderby", it) }
        input.sort.digest?.let { put("digest", it.toString()) }
        input.typeId?.let { put("typeid", it.toString()) }
    }
}

fun parseForumThreads(
    variables: JSONObject,
    hasUnknownTotal: Boolean = false,
): YamiboForumThreadsPage {
    val rawThreads = variables.opt("forum_threadlist") as? JSONArray
        ?: invalidResponse("百合会未返回帖子列表数据")
    val rawSubforums = variables.opt("sublist") as? JSONArray
        ?: invalidResponse("百合会未返回帖子列表数据")
    val forum = parseThreadForum(variables.opt("forum"))
    val page = scalarInt(variables.opt("page"), "page").takeIf { it > 0 }
        ?: invalidResponse("百合会返回了无效的分页数据")
    val pageSize = scalarInt(variables.opt("tpp"), "tpp").takeIf { it > 0 }
        ?: invalidResponse("百合会返回了无效的分页数据")
    val threadTypes = parseThreadTypes(variables.opt("threadtypes"))
    val typeNames = threadTypes.associate { it.id to it.name }
    val totalPages = ceil(forum.threadCount.toDouble() / pageSize).toInt()
    val threads = rawThreads.strictObjects("百合会返回了无效的帖子数据").map {
        parseThread(it, typeNames)
    }
    return YamiboForumThreadsPage(
        forum = forum,
        pagination = YamiboForumPagination(
            hasNextPage = if (hasUnknownTotal) threads.size >= pageSize else page < totalPages,
            page = page,
        ),
        subforums = rawSubforums.strictObjects("百合会返回了无效的子板块数据").map(::parseThreadSubforum),
        threadTypes = threadTypes,
        threads = threads,
    )
}

private fun parseThreadForum(raw: Any?): YamiboForumDetails {
    val value = raw as? JSONObject ?: invalidResponse("百合会返回了无效的板块详情")
    if (!value.stringOrNull("redirect").isNullOrEmpty()) {
        throw YamiboApiException(YamiboApiErrorCode.SERVER_ERROR, "该板块是外部链接", "forum_redirect")
    }
    return YamiboForumDetails(
        name = value.threadString("name"),
        threadCount = value.threadNonNegative("threadcount"),
    )
}

private fun parseThreadSubforum(value: JSONObject) = YamiboForumChild(
    id = value.threadPositive("fid"),
    name = value.threadString("name"),
    threadCount = value.threadNonNegative("threads"),
)

private fun parseThread(value: JSONObject, typeNames: Map<Int, String>): YamiboThread {
    val typeId = optionalPositive(value.opt("typeid"), "typeid")
    return YamiboThread(
        author = YamiboThreadAuthor(value.threadString("author")),
        createdAtText = value.threadString("dateline"),
        id = value.threadPositive("tid"),
        replyCount = value.threadNonNegative("replies"),
        stickyLevel = value.threadNonNegative("displayorder"),
        subject = value.threadString("subject"),
        typeName = typeId?.let(typeNames::get),
    )
}

private fun parseThreadTypes(raw: Any?): List<YamiboThreadType> {
    if (raw == null || raw == JSONObject.NULL) return emptyList()
    val value = raw as? JSONObject ?: invalidResponse("百合会返回了无效的主题分类数据")
    val types = value.opt("types") as? JSONObject ?: invalidResponse("百合会返回了无效的主题分类数据")
    return types.keys().asSequence().map { rawId ->
        val id = rawId.toIntOrNull()?.takeIf { it > 0 }
            ?: invalidResponse("百合会返回了无效的主题分类数据")
        val name = types.opt(rawId) as? String ?: invalidResponse("百合会返回了无效的主题分类数据")
        YamiboThreadType(id, name)
    }.sortedBy(YamiboThreadType::id).toList()
}

private fun JSONObject.threadString(key: String, fallback: String? = null): String {
    val raw = opt(key)
    if ((raw == null || raw == JSONObject.NULL) && fallback != null) return fallback
    return raw as? String ?: invalidResponse("百合会帖子数据缺少有效的 $key 字段")
}

private fun JSONObject.threadNonNegative(key: String, fallback: Int = 0): Int {
    val raw = opt(key)
    val value = if (raw == null || raw == JSONObject.NULL || raw == "") fallback else scalarInt(raw, key)
    if (value < 0) invalidResponse("百合会帖子数据包含无效的 $key 字段")
    return value
}

private fun JSONObject.threadPositive(key: String): Int {
    val value = scalarInt(opt(key), key)
    if (value <= 0) invalidResponse("百合会帖子数据包含无效的 $key 字段")
    return value
}

private fun scalarInt(raw: Any?, field: String): Int = when (raw) {
    is Int -> raw
    is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    is String -> raw.toIntOrNull()
    else -> null
} ?: invalidResponse("百合会帖子数据包含无效的 $field 字段")

private fun optionalPositive(raw: Any?, field: String): Int? {
    if (raw == null || raw == JSONObject.NULL || raw == "" || raw == "0" || raw == 0) return null
    val value = scalarInt(raw, field)
    if (value <= 0) invalidResponse("百合会帖子数据包含无效的 $field 字段")
    return value
}

private fun JSONArray.strictObjects(error: String): List<JSONObject> =
    (0 until length()).map { opt(it) as? JSONObject ?: invalidResponse(error) }
