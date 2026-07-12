package com.yamibo.pocket300.api

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

data class GetThreadPostsInput(val threadId: Int, val page: Int = 1)

data class YamiboPostAuthor(
    val avatarUrl: String?,
    val groupIconId: String?,
    val groupId: Int?,
    val id: Int?,
    val isAnonymous: Boolean,
    val name: String,
)

data class YamiboPostComment(
    val author: YamiboPostAuthor,
    val createdAtText: String,
    val id: Int,
    val message: String,
    val postId: Int,
    val threadId: Int,
)

data class YamiboPostAttachment(
    val id: Int,
    val filename: String,
    val isImage: Boolean,
    val url: String,
)

data class YamiboPost(
    val attachments: List<YamiboPostAttachment>,
    val author: YamiboPostAuthor,
    val comments: List<YamiboPostComment>,
    val createdAt: Long,
    val createdAtText: String,
    /** Discuz-rendered, untrusted HTML. Render only with a restrictive HTML policy. */
    val html: String,
    val hasAttachment: Boolean,
    val id: Int,
    val isOriginalPost: Boolean,
    val number: Int,
    val position: Int,
    val replyCredit: Int,
    val status: Int,
    val threadId: Int,
)

data class YamiboPollOption(
    val color: String?,
    val id: Int,
    val percentage: Double,
    val text: String,
    val voteCount: Int,
)

data class YamiboThreadPoll(
    val canVote: Boolean,
    val expiresAt: Long?,
    val maxChoices: Int,
    val multiple: Boolean,
    val options: List<YamiboPollOption>,
    val resultsVisible: Boolean,
    val voterCount: Int,
)

data class YamiboThreadDetails(
    val author: YamiboPostAuthor,
    val createdAt: Long,
    val digestLevel: Int,
    val forumId: Int,
    val heat: Int,
    val hasAttachment: Boolean,
    val id: Int,
    val isClosed: Boolean,
    val lastPoster: String,
    val lastPostAtText: String,
    val maxPosition: Int,
    val price: Int,
    val readPermission: Int,
    val recommendationCount: Int,
    val replyCount: Int,
    val specialType: YamiboThreadSpecialType,
    val specialTypeId: Int,
    val subject: String,
    val typeId: Int?,
    val viewCount: Int,
    val webUrl: String,
)

data class YamiboThreadPostsPagination(
    val hasNextPage: Boolean,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalPosts: Int,
)

data class YamiboThreadPostsPage(
    val pagination: YamiboThreadPostsPagination,
    val poll: YamiboThreadPoll?,
    val posts: List<YamiboPost>,
    val thread: YamiboThreadDetails,
)

class YamiboPostsApi(private val client: YamiboClient) {
    suspend fun getThreadPosts(input: GetThreadPostsInput): YamiboThreadPostsPage {
        require(input.threadId > 0) { "threadId must be a positive integer" }
        require(input.page > 0) { "page must be a positive integer" }
        val response = client.requestMobileApi(
            mapOf("module" to "viewthread", "page" to input.page.toString(), "tid" to input.threadId.toString()),
        )
        val serverCode = response.message?.code?.takeIf(String::isNotBlank) ?: response.error
        if (serverCode != null) {
            val denied = serverCode in setOf("thread_nonexistence", "viewperm_none_nopermission", "group_nopermission")
            throw YamiboApiException(
                YamiboApiErrorCode.SERVER_ERROR,
                if (denied) "主题不存在或当前账号无权访问" else {
                    response.message?.message?.takeIf(String::isNotBlank) ?: "百合会主题服务返回了错误"
                },
                serverCode,
            )
        }
        return parseThreadPosts(
            response.variables ?: invalidResponse("百合会未返回主题楼层数据"),
            input.page,
        )
    }
}

fun parseThreadPosts(variables: JSONObject, requestedPage: Int = 1): YamiboThreadPostsPage {
    require(requestedPage > 0) { "requestedPage must be a positive integer" }
    val rawPosts = variables.opt("postlist") as? JSONArray ?: invalidResponse("百合会未返回主题楼层数据")
    val thread = parsePostThread(variables.opt("thread"))
    val pageSize = postScalarInt(variables.opt("ppp"), "ppp").takeIf { it > 0 }
        ?: invalidResponse("百合会返回了无效的主题分页数据")
    val comments = parseComments(variables.opt("comments"))
    val posts = rawPosts.postObjects("百合会返回了无效的楼层数据").map { parsePost(it, comments) }
    if (posts.any { it.threadId != thread.id }) invalidResponse("百合会楼层与主题 ID 不一致")
    val totalPosts = thread.replyCount + 1
    val totalPages = ceil(totalPosts.toDouble() / pageSize).toInt()
    return YamiboThreadPostsPage(
        pagination = YamiboThreadPostsPagination(
            requestedPage < totalPages,
            requestedPage,
            pageSize,
            totalPages,
            totalPosts,
        ),
        poll = parsePoll(variables.opt("special_poll")),
        posts = posts,
        thread = thread,
    )
}

private fun parsePostThread(raw: Any?): YamiboThreadDetails {
    val value = raw as? JSONObject ?: invalidResponse("百合会未返回有效的主题详情")
    val id = value.postPositive("tid")
    val specialId = value.postNonNegative("special")
    return YamiboThreadDetails(
        author = parseSummaryAuthor(value),
        createdAt = value.postTimestamp("dateline"),
        digestLevel = value.postNonNegative("digest"),
        forumId = value.postPositive("fid"),
        heat = value.postNonNegative("heats"),
        hasAttachment = value.postFlag("attachment"),
        id = id,
        isClosed = value.postFlag("closed"),
        lastPoster = value.postString("lastposter"),
        lastPostAtText = value.postString("lastpost"),
        maxPosition = value.postNonNegative("maxposition"),
        price = value.postNonNegative("price"),
        readPermission = value.postNonNegative("readperm"),
        recommendationCount = value.postNonNegative("recommend_add"),
        replyCount = value.postNonNegative("replies"),
        specialType = YamiboThreadSpecialType.entries.getOrNull(specialId) ?: YamiboThreadSpecialType.UNKNOWN,
        specialTypeId = specialId,
        subject = value.postString("subject"),
        typeId = postOptionalPositive(value.opt("typeid"), "typeid"),
        viewCount = value.postNonNegative("views"),
        webUrl = "$YAMIBO_ORIGIN/forum.php?mod=viewthread&tid=$id&mobile=2",
    )
}

private fun parsePost(value: JSONObject, comments: Map<Int, List<YamiboPostComment>>): YamiboPost {
    val id = value.postPositive("pid")
    val position = value.postPositive("position")
    return YamiboPost(
        attachments = parsePostAttachments(value.opt("attachments") ?: value.opt("attachlist")),
        author = parseFloorAuthor(value),
        comments = comments[id].orEmpty(),
        createdAt = value.postTimestamp("dbdateline"),
        createdAtText = value.postString("dateline"),
        html = value.postString("message"),
        hasAttachment = value.postFlag("attachment"),
        id = id,
        isOriginalPost = value.postFlag("first"),
        number = postDisplayNumber(value.opt("number"), position),
        position = position,
        replyCredit = value.postNonNegative("replycredit"),
        status = value.postNonNegative("status"),
        threadId = value.postPositive("tid"),
    )
}

private fun postDisplayNumber(raw: Any?, position: Int): Int = when (raw) {
    is Int -> raw
    is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    is String -> raw.toIntOrNull()
    else -> null
}?.takeIf { it > 0 } ?: position

private fun parsePostAttachments(raw: Any?): List<YamiboPostAttachment> {
    if (raw == null || raw == JSONObject.NULL || (raw is JSONArray && raw.length() == 0)) return emptyList()
    val values = when (raw) {
        is JSONObject -> raw.keys().asSequence().map { raw.opt(it) }.toList()
        is JSONArray -> (0 until raw.length()).map(raw::opt)
        else -> invalidResponse("百合会返回了无效的附件数据")
    }
    return values.map { item ->
        val value = item as? JSONObject ?: invalidResponse("百合会返回了无效的附件数据")
        val id = value.postPositive("aid")
        val attachment = value.postString("attachment", "").trim()
        val baseUrl = value.postString("url", "").trim()
        val directUrl = when {
            attachment.startsWith("http://") || attachment.startsWith("https://") || attachment.startsWith("//") -> attachment
            baseUrl.isNotEmpty() && attachment.isNotEmpty() -> "${baseUrl.trimEnd('/')}/${attachment.trimStart('/')}"
            baseUrl.isNotEmpty() -> baseUrl
            attachment.isNotEmpty() -> "/data/attachment/forum/${attachment.trimStart('/')}"
            else -> "$YAMIBO_ORIGIN/forum.php?mod=attachment&aid=$id"
        }
        YamiboPostAttachment(
            id = id,
            filename = value.postString("filename", attachment.substringAfterLast('/')).ifBlank { "附件 $id" },
            isImage = attachmentIsImage(value.opt("isimage"), attachment, baseUrl),
            url = normalizePostUrl(directUrl) ?: invalidResponse("百合会返回了无效的附件地址"),
        )
    }
}

private val postImageExtensions = setOf("avif", "bmp", "gif", "jpeg", "jpg", "png", "webp")

private fun attachmentIsImage(raw: Any?, vararg paths: String): Boolean {
    val declared = when (raw) {
        is Number -> raw.toInt().takeIf { it >= 0 }?.let { it > 0 }
        is String -> raw.toIntOrNull()?.takeIf { it >= 0 }?.let { it > 0 }
            ?: raw.lowercase().takeIf { it.startsWith("image/") }?.let { true }
        else -> null
    }
    return declared ?: paths.any { path ->
        path.substringBefore('?').substringAfterLast('.', "").lowercase() in postImageExtensions
    }
}

private fun parseComments(raw: Any?): Map<Int, List<YamiboPostComment>> {
    if (raw == null || raw == JSONObject.NULL || (raw is JSONArray && raw.length() == 0)) return emptyMap()
    val value = raw as? JSONObject ?: invalidResponse("百合会返回了无效的楼中点评数据")
    return value.keys().asSequence().associate { rawPostId ->
        val postId = rawPostId.toIntOrNull()?.takeIf { it > 0 }
            ?: invalidResponse("百合会返回了无效的楼中点评数据")
        val list = value.opt(rawPostId) as? JSONArray ?: invalidResponse("百合会返回了无效的楼中点评数据")
        val comments = list.postObjects("百合会返回了无效的楼中点评").map(::parseComment)
        if (comments.any { it.postId != postId }) invalidResponse("百合会楼中点评与楼层 ID 不一致")
        postId to comments
    }
}

private fun parseComment(value: JSONObject) = YamiboPostComment(
    author = parseCommentAuthor(value),
    createdAtText = value.postString("dateline"),
    id = value.postPositive("id"),
    message = value.postString("comment"),
    postId = value.postPositive("pid"),
    threadId = value.postPositive("tid"),
)

private fun parseSummaryAuthor(value: JSONObject): YamiboPostAuthor {
    val id = postOptionalPositive(value.opt("authorid"), "authorid")
    return YamiboPostAuthor(avatarForPostUser(id), null, null, id, id == null, value.postString("author"))
}

private fun parseFloorAuthor(value: JSONObject): YamiboPostAuthor {
    val id = postOptionalPositive(value.opt("authorid"), "authorid")
    val anonymous = value.postFlag("anonymous")
    return YamiboPostAuthor(
        avatarUrl = if (anonymous) null else avatarForPostUser(id),
        groupIconId = value.postString("groupiconid", "").trim().ifEmpty { null },
        groupId = postOptionalPositive(value.opt("groupid"), "groupid"),
        id = if (anonymous) null else id,
        isAnonymous = anonymous,
        name = value.postString("author"),
    )
}

private fun parseCommentAuthor(value: JSONObject): YamiboPostAuthor {
    val id = postOptionalPositive(value.opt("authorid"), "authorid")
    val avatar = normalizePostUrl(value.postString("avatar", "")) ?: avatarForPostUser(id)
    return YamiboPostAuthor(avatar, null, null, id, id == null, value.postString("author"))
}

private fun parsePoll(raw: Any?): YamiboThreadPoll? {
    if (raw == null || raw == JSONObject.NULL || (raw is JSONArray && raw.length() == 0)) return null
    val value = raw as? JSONObject ?: invalidResponse("百合会返回了无效的投票数据")
    val options = value.opt("polloptions") as? JSONObject ?: invalidResponse("百合会返回了无效的投票数据")
    val expiration = when (val expirationRaw = value.opt("expirations")) {
        null, JSONObject.NULL, "" -> 0
        else -> postScalarInt(expirationRaw, "expirations")
    }
    if (expiration < 0) invalidResponse("百合会投票数据包含无效的截止时间")
    return YamiboThreadPoll(
        canVote = value.postFlag("allowvote"),
        expiresAt = expiration.takeIf { it > 0 }?.toLong()?.times(1_000),
        maxChoices = value.postPositive("maxchoices"),
        multiple = value.postFlag("multiple"),
        options = options.keys().asSequence().map { key ->
            val option = options.opt(key) as? JSONObject ?: invalidResponse("百合会返回了无效的投票选项")
            YamiboPollOption(
                color = parsePollColor(option.opt("color")),
                id = option.postPositive("polloptionid"),
                percentage = postDouble(option.opt("percent"), "percent").also {
                    if (it !in 0.0..100.0) invalidResponse("百合会投票数据包含无效的 percent 字段")
                },
                text = option.postString("polloption"),
                voteCount = option.postNonNegative("votes"),
            )
        }.toList(),
        resultsVisible = value.postFlag("visiblepoll"),
        voterCount = value.postNonNegative("voterscount"),
    )
}

private fun parsePollColor(raw: Any?): String? {
    if (raw == null || raw == JSONObject.NULL || raw == "") return null
    val value = raw as? String ?: invalidResponse("百合会投票数据包含无效的颜色字段")
    if (!Regex("""^[0-9a-f]{6}$""", RegexOption.IGNORE_CASE).matches(value)) {
        invalidResponse("百合会投票数据包含无效的颜色字段")
    }
    return "#$value"
}

private fun normalizePostUrl(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith('/') -> "$YAMIBO_ORIGIN$value"
        value.startsWith("http://") || value.startsWith("https://") -> value
        else -> "$YAMIBO_ORIGIN/${value.trimStart('/')}"
    }
}

private fun avatarForPostUser(id: Int?) = id?.let { "$YAMIBO_ORIGIN/uc_server/avatar.php?uid=$it&size=small" }

private fun JSONObject.postString(key: String, fallback: String? = null): String {
    val raw = opt(key)
    if ((raw == null || raw == JSONObject.NULL) && fallback != null) return fallback
    return raw as? String ?: invalidResponse("百合会主题数据缺少有效的 $key 字段")
}

private fun JSONObject.postNonNegative(key: String, fallback: Int = 0): Int {
    val raw = opt(key)
    val value = if (raw == null || raw == JSONObject.NULL || raw == "") fallback else postScalarInt(raw, key)
    if (value < 0) invalidResponse("百合会主题数据包含无效的 $key 字段")
    return value
}

private fun JSONObject.postPositive(key: String): Int {
    val value = postScalarInt(opt(key), key)
    if (value <= 0) invalidResponse("百合会主题数据包含无效的 $key 字段")
    return value
}

private fun JSONObject.postFlag(key: String) = postNonNegative(key) > 0
private fun JSONObject.postTimestamp(key: String) = postNonNegative(key).toLong() * 1_000

private fun postScalarInt(raw: Any?, field: String): Int = when (raw) {
    is Int -> raw
    is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    is String -> raw.toIntOrNull()
    else -> null
} ?: invalidResponse("百合会主题数据包含无效的 $field 字段")

private fun postDouble(raw: Any?, field: String): Double = when (raw) {
    is Number -> raw.toDouble()
    is String -> raw.toDoubleOrNull()
    else -> null
}?.takeIf(Double::isFinite) ?: invalidResponse("百合会投票数据包含无效的 $field 字段")

private fun postOptionalPositive(raw: Any?, field: String): Int? {
    if (raw == null || raw == JSONObject.NULL || raw == "" || raw == "0" || raw == 0) return null
    val value = postScalarInt(raw, field)
    if (value <= 0) invalidResponse("百合会主题数据包含无效的 $field 字段")
    return value
}

private fun JSONArray.postObjects(error: String): List<JSONObject> =
    (0 until length()).map { opt(it) as? JSONObject ?: invalidResponse(error) }
