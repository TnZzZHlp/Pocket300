package com.yamibo.pocket300.api

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
    LATEST_REPLY("lastpost", "lastpost", digest = 1),
    POPULAR("heat", "heats"),
    DIGEST("digest", "heats", digest = 1),
    NEWEST("dateline", "dateline"),
}

data class YamiboForumDetails(
    val autoCloseDays: Int,
    val description: String,
    val hasPassword: Boolean,
    val iconUrl: String?,
    val id: Int,
    val name: String,
    val parentId: Int?,
    val postCount: Int,
    val rules: String,
    val threadCount: Int,
    val usesPictureList: Boolean,
)

data class YamiboForumChild(
    val iconUrl: String?,
    val id: Int,
    val name: String,
    val postCount: Int,
    val threadCount: Int,
    val todayPostCount: Int,
)

data class YamiboThreadType(val iconUrl: String?, val id: Int, val name: String)

data class YamiboThreadAuthor(
    val avatarUrl: String?,
    val groupIconId: String?,
    val id: Int?,
    val name: String,
)

data class YamiboThreadReplyPreview(val author: YamiboThreadAuthor, val id: Int, val message: String)

data class YamiboThreadImage(
    val fileName: String,
    val fileSize: Int,
    val height: Int?,
    val id: Int,
    val path: String,
    val url: String?,
    val width: Int?,
)

enum class YamiboThreadSpecialType { NORMAL, POLL, TRADE, REWARD, ACTIVITY, DEBATE, UNKNOWN }

data class YamiboThread(
    val author: YamiboThreadAuthor,
    val createdAt: Long,
    val createdAtText: String,
    val digestLevel: Int,
    val excerpt: String?,
    val hasAttachment: Boolean,
    val id: Int,
    val imageCount: Int,
    val images: List<YamiboThreadImage>,
    val isRecommendedByViewer: Boolean,
    val isRushReply: Boolean,
    val lastPostAt: Long,
    val lastPostAtText: String,
    val lastPoster: String,
    val price: Int,
    val readPermission: Int,
    val recommendationCount: Int,
    val replies: List<YamiboThreadReplyPreview>,
    val replyCount: Int,
    val replyCredit: Int,
    val specialType: YamiboThreadSpecialType,
    val specialTypeId: Int,
    val stickyLevel: Int,
    val subject: String,
    val typeId: Int?,
    val typeName: String?,
    val viewCount: Int,
)

data class YamiboForumPagination(
    val hasNextPage: Boolean,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalThreads: Int,
)

data class YamiboViewerGroup(val id: Int, val name: String)

data class YamiboForumThreadsPage(
    val forum: YamiboForumDetails,
    val pagination: YamiboForumPagination,
    val rewardUnit: String,
    val subforums: List<YamiboForumChild>,
    val threadTypes: List<YamiboThreadType>,
    val threads: List<YamiboThread>,
    val viewerGroup: YamiboViewerGroup,
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
    val groupIcons = parseGroupIcons(variables.opt("groupiconid"))
    val totalPages = ceil(forum.threadCount.toDouble() / pageSize).toInt()
    val threads = rawThreads.strictObjects("百合会返回了无效的帖子数据").map {
        parseThread(it, typeNames, groupIcons)
    }
    return YamiboForumThreadsPage(
        forum = forum,
        pagination = YamiboForumPagination(
            hasNextPage = if (hasUnknownTotal) threads.size >= pageSize else page < totalPages,
            page = page,
            pageSize = pageSize,
            totalPages = totalPages,
            totalThreads = forum.threadCount,
        ),
        rewardUnit = variables.stringOrNull("reward_unit").orEmpty(),
        subforums = rawSubforums.strictObjects("百合会返回了无效的子板块数据").map(::parseThreadSubforum),
        threadTypes = threadTypes,
        threads = threads,
        viewerGroup = parseViewerGroup(variables.opt("group")),
    )
}

private fun parseThreadForum(raw: Any?): YamiboForumDetails {
    val value = raw as? JSONObject ?: invalidResponse("百合会返回了无效的板块详情")
    if (!value.stringOrNull("redirect").isNullOrEmpty()) {
        throw YamiboApiException(YamiboApiErrorCode.SERVER_ERROR, "该板块是外部链接", "forum_redirect")
    }
    return YamiboForumDetails(
        autoCloseDays = value.threadNonNegative("autoclose"),
        description = value.threadString("description", ""),
        hasPassword = value.threadFlag("password"),
        iconUrl = value.threadString("icon", "").trim().ifEmpty { null },
        id = value.threadPositive("fid"),
        name = value.threadString("name"),
        parentId = optionalPositive(value.opt("fup"), "fup"),
        postCount = value.threadNonNegative("posts"),
        rules = value.threadString("rules", ""),
        threadCount = value.threadNonNegative("threadcount"),
        usesPictureList = value.threadFlag("picstyle"),
    )
}

private fun parseThreadSubforum(value: JSONObject) = YamiboForumChild(
    iconUrl = value.threadString("icon", "").trim().ifEmpty { null },
    id = value.threadPositive("fid"),
    name = value.threadString("name"),
    postCount = value.threadNonNegative("posts"),
    threadCount = value.threadNonNegative("threads"),
    todayPostCount = value.threadNonNegative("todayposts"),
)

private fun parseViewerGroup(raw: Any?): YamiboViewerGroup {
    val value = raw as? JSONObject ?: invalidResponse("百合会返回了无效的用户组数据")
    return YamiboViewerGroup(value.threadPositive("groupid"), value.threadString("grouptitle"))
}

private fun parseThread(
    value: JSONObject,
    typeNames: Map<Int, String>,
    groupIcons: Map<Int, String>,
): YamiboThread {
    val replies = optionalArray(value.opt("reply"), "百合会返回了无效的帖子预览数据")
    val rawImages = optionalArray(value.opt("attachmentImagePreviewList"), "百合会返回了无效的帖子预览数据")
    // Discuz inserts empty arrays for deleted attachments; preserve the thread and skip those slots.
    val images = (0 until rawImages.length()).mapNotNull { rawImages.opt(it) as? JSONObject }.map(::parseThreadImage)
    val typeId = optionalPositive(value.opt("typeid"), "typeid")
    val specialId = value.threadNonNegative("special")
    return YamiboThread(
        author = parseThreadAuthor(value.threadString("author"), value.opt("authorid"), groupIcons),
        createdAt = value.threadNonNegative("dbdateline").toLong() * 1_000,
        createdAtText = value.threadString("dateline"),
        digestLevel = value.threadNonNegative("digest"),
        excerpt = value.threadString("message", "").trim().ifEmpty { null },
        hasAttachment = value.threadFlag("attachment"),
        id = value.threadPositive("tid"),
        imageCount = value.threadNonNegative("attachmentImageNumber", images.size),
        images = images,
        isRecommendedByViewer = value.threadFlag("recommend"),
        isRushReply = value.threadFlag("rushreply"),
        lastPostAt = value.threadNonNegative("dblastpost").toLong() * 1_000,
        lastPostAtText = value.threadString("lastpost"),
        lastPoster = value.threadString("lastposter"),
        price = value.threadNonNegative("price"),
        readPermission = value.threadNonNegative("readperm"),
        recommendationCount = value.threadNonNegative("recommend_add"),
        replies = replies.strictObjects("百合会返回了无效的热门回复数据").map {
            YamiboThreadReplyPreview(
                author = parseThreadAuthor(it.threadString("author"), it.opt("authorid"), groupIcons),
                id = it.threadPositive("pid"),
                message = it.threadString("message"),
            )
        },
        replyCount = value.threadNonNegative("replies"),
        replyCredit = value.threadNonNegative("replycredit"),
        specialType = YamiboThreadSpecialType.entries.getOrNull(specialId) ?: YamiboThreadSpecialType.UNKNOWN,
        specialTypeId = specialId,
        stickyLevel = value.threadNonNegative("displayorder"),
        subject = value.threadString("subject"),
        typeId = typeId,
        typeName = typeId?.let(typeNames::get),
        viewCount = value.threadNonNegative("views"),
    )
}

private fun parseThreadImage(value: JSONObject): YamiboThreadImage {
    val path = value.threadString("attachment")
    val isRemote = value.threadFlag("remote")
    val width = value.threadNonNegative("width").takeIf { it > 0 }
    val height = value.threadNonNegative("height").takeIf { it > 0 }
    return YamiboThreadImage(
        fileName = value.threadString("filename"),
        fileSize = value.threadNonNegative("filesize"),
        height = height,
        id = value.threadPositive("aid"),
        path = path,
        url = if (path.isEmpty() || isRemote) null else {
            val encoded = path.split('/').joinToString("/") {
                URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20")
            }
            "$YAMIBO_ORIGIN/data/attachment/forum/$encoded"
        },
        width = width,
    )
}

private fun parseThreadAuthor(name: String, rawId: Any?, icons: Map<Int, String>): YamiboThreadAuthor {
    val id = optionalPositive(rawId, "authorid")
    return YamiboThreadAuthor(
        avatarUrl = id?.let { "$YAMIBO_ORIGIN/uc_server/avatar.php?uid=$it&size=small" },
        groupIconId = id?.let(icons::get),
        id = id,
        name = name,
    )
}

private fun parseGroupIcons(raw: Any?): Map<Int, String> {
    if (raw == null || raw == JSONObject.NULL) return emptyMap()
    // Discuz serializes an empty PHP associative array as [] instead of {}.
    if (raw is JSONArray && raw.length() == 0) return emptyMap()
    val value = raw as? JSONObject ?: invalidResponse("百合会返回了无效的用户组图标数据")
    return value.keys().asSequence().associate { rawId ->
        val id = rawId.toIntOrNull()?.takeIf { it > 0 }
            ?: invalidResponse("百合会返回了无效的用户组图标数据")
        val icon = value.opt(rawId) as? String
            ?: invalidResponse("百合会返回了无效的用户组图标数据")
        id to icon
    }
}

private fun parseThreadTypes(raw: Any?): List<YamiboThreadType> {
    if (raw == null || raw == JSONObject.NULL) return emptyList()
    val value = raw as? JSONObject ?: invalidResponse("百合会返回了无效的主题分类数据")
    val types = value.opt("types") as? JSONObject ?: invalidResponse("百合会返回了无效的主题分类数据")
    val icons = value.opt("icons") as? JSONObject
    return types.keys().asSequence().map { rawId ->
        val id = rawId.toIntOrNull()?.takeIf { it > 0 }
            ?: invalidResponse("百合会返回了无效的主题分类数据")
        val name = types.opt(rawId) as? String ?: invalidResponse("百合会返回了无效的主题分类数据")
        val rawIcon = icons?.opt(rawId)
        if (rawIcon != null && rawIcon != JSONObject.NULL && rawIcon !is String) {
            invalidResponse("百合会返回了无效的主题分类图标")
        }
        YamiboThreadType((rawIcon as? String)?.trim()?.ifEmpty { null }, id, name)
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

private fun JSONObject.threadFlag(key: String) = threadNonNegative(key) > 0

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

private fun optionalArray(raw: Any?, error: String): JSONArray = when (raw) {
    null, JSONObject.NULL -> JSONArray()
    is JSONArray -> raw
    else -> invalidResponse(error)
}

private fun JSONArray.strictObjects(error: String): List<JSONObject> =
    (0 until length()).map { opt(it) as? JSONObject ?: invalidResponse(error) }
