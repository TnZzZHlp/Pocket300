package com.yamibo.pocket300.api

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

data class GetMessageThreadsInput(val page: Int = 1, val pageSize: Int = 15)
data class GetPrivateConversationInput(val userId: Int, val page: Int = 1, val pageSize: Int = 5)
data class YamiboMessageUser(val avatarUrl: String, val id: Int, val name: String)
enum class YamiboMessageThreadKind { GROUP, PRIVATE }
data class YamiboMessagePreview(
    val author: YamiboMessageUser?,
    val createdAt: Long,
    val createdAtText: String,
    val text: String,
)

data class YamiboMessageThread(
    val id: Int,
    val isUnread: Boolean,
    val kind: YamiboMessageThreadKind,
    val lastMessage: YamiboMessagePreview,
    val partner: YamiboMessageUser?,
    val participantCount: Int?,
    val subject: String?,
    val webUrl: String,
)

data class YamiboMessagePagination(
    val hasNextPage: Boolean,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalItems: Int,
)

data class YamiboMessageThreadsPage(val pagination: YamiboMessagePagination, val threads: List<YamiboMessageThread>)
data class YamiboPrivateMessage(
    val author: YamiboMessageUser,
    val createdAt: Long,
    val createdAtText: String,
    val id: Int,
    val isFromViewer: Boolean,
    val recipient: YamiboMessageUser,
    val text: String,
)

data class YamiboPrivateConversationPage(
    val messages: List<YamiboPrivateMessage>,
    val pagination: YamiboMessagePagination,
    val partner: YamiboMessageUser,
    val webUrl: String,
)

enum class YamiboMessageErrorCode { NOT_AUTHENTICATED, INVALID_RESPONSE, NETWORK_ERROR, SERVER_ERROR }
class YamiboMessageException(
    val code: YamiboMessageErrorCode,
    message: String,
    val serverCode: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class YamiboMessagesApi(private val client: YamiboClient) {
    suspend fun getMessageThreads(input: GetMessageThreadsInput = GetMessageThreadsInput()): YamiboMessageThreadsPage {
        validateMessageInput(input.page, input.pageSize)
        return parseMessageThreads(request(mapOf("page" to input.page.toString(), "perpage" to input.pageSize.toString())))
    }

    suspend fun getPrivateConversation(input: GetPrivateConversationInput): YamiboPrivateConversationPage {
        require(input.userId > 0) { "userId must be a positive integer" }
        validateMessageInput(input.page, input.pageSize)
        return parsePrivateConversation(
            request(
                mapOf(
                    "page" to input.page.toString(), "perpage" to input.pageSize.toString(),
                    "subop" to "view", "touid" to input.userId.toString(),
                ),
            ),
            input.userId,
        )
    }

    private suspend fun request(parameters: Map<String, String>): JSONObject {
        val response = try {
            client.requestMobileApi(parameters + mapOf("module" to "mypm", "mapifrom" to "ios", "smiley" to "no"))
        } catch (error: YamiboApiException) {
            throw YamiboMessageException(error.code.toMessageCode(), error.message ?: "百合会请求失败", error.serverCode, error)
        }
        val serverCode = response.message?.code?.takeIf(String::isNotBlank) ?: response.error
        if (serverCode != null) {
            val login = serverCode.startsWith("login_before_enter_home") || serverCode.startsWith("to_login")
            throw YamiboMessageException(
                if (login) YamiboMessageErrorCode.NOT_AUTHENTICATED else YamiboMessageErrorCode.SERVER_ERROR,
                if (login) "请先登录百合会" else response.message?.message?.takeIf(String::isNotBlank) ?: "百合会消息服务返回了错误",
                serverCode,
            )
        }
        return response.variables ?: messageInvalid("百合会未返回消息数据")
    }
}

fun parseMessageThreads(variables: JSONObject): YamiboMessageThreadsPage {
    val list = variables.opt("list") as? JSONArray ?: messageInvalid("百合会未返回消息会话列表")
    val viewerId = messagePositive(variables.opt("member_uid"), "member_uid")
    val viewerName = variables.stringOrNull("member_username").orEmpty()
    return YamiboMessageThreadsPage(
        parseMessagePagination(variables),
        list.messageObjects("百合会返回了无效的消息会话").map { parseMessageThread(it, viewerId, viewerName) },
    )
}

fun parsePrivateConversation(variables: JSONObject, partnerId: Int): YamiboPrivateConversationPage {
    require(partnerId > 0) { "partnerId must be a positive integer" }
    val list = variables.opt("list") as? JSONArray ?: messageInvalid("百合会未返回私信记录")
    val viewerId = messagePositive(variables.opt("member_uid"), "member_uid")
    val viewerName = variables.stringOrNull("member_username").orEmpty()
    val messages = list.messageObjects("百合会返回了无效的私信").map { parsePrivateMessage(it, viewerId, viewerName) }
    val partner = messages.asSequence().flatMap { sequenceOf(it.author, it.recipient) }.firstOrNull { it.id == partnerId }
    if (messages.isNotEmpty() && partner == null) messageInvalid("百合会返回的私信与请求会员不一致")
    return YamiboPrivateConversationPage(
        messages,
        parseMessagePagination(variables),
        partner ?: messageUser(partnerId, ""),
        "$YAMIBO_ORIGIN/home.php?mod=space&do=pm&subop=view&touid=$partnerId&mobile=2",
    )
}

private fun parseMessageThread(value: JSONObject, viewerId: Int, viewerName: String): YamiboMessageThread {
    val id = messagePositive(if (value.has("plid")) value.opt("plid") else value.opt("pmid"), "plid")
    val partnerId = messageOptionalPositive(value.opt("touid"), "touid")
    val lastAuthorId = messageOptionalPositive(value.opt("msgfromid"), "msgfromid")
    val partnerName = value.messageString("tousername", "").trim()
    val authorName = value.messageString("msgfrom", "").trim()
    val participantCount = messageOptionalPositive(value.opt("numbers"), "numbers")
    val subject = value.messageString("subject", "").trim().ifEmpty { null }
    val kind = if (partnerId != null) YamiboMessageThreadKind.PRIVATE else YamiboMessageThreadKind.GROUP
    val partner = partnerId?.let { messageUser(it, partnerName) }
    val author = lastAuthorId?.let { authorId ->
        val name = authorName.ifEmpty {
            when (authorId) {
                viewerId -> viewerName
                partnerId -> partnerName
                else -> ""
            }
        }
        messageUser(authorId, name)
    }
    return YamiboMessageThread(
        id,
        messageNonNegative(if (value.has("isnew")) value.opt("isnew") else 0, "isnew") > 0,
        kind,
        YamiboMessagePreview(
            author,
            messageNonNegative(value.opt("dateline"), "dateline").toLong() * 1_000,
            value.messageString("vdateline", ""),
            messageText(value.messageString("message", "")),
        ),
        partner,
        if (kind == YamiboMessageThreadKind.GROUP) participantCount else null,
        if (kind == YamiboMessageThreadKind.GROUP) subject else null,
        if (partnerId != null) "$YAMIBO_ORIGIN/home.php?mod=space&do=pm&subop=view&touid=$partnerId&mobile=2"
        else "$YAMIBO_ORIGIN/home.php?mod=space&do=pm&subop=view&plid=$id&type=1&mobile=2",
    )
}

private fun parsePrivateMessage(value: JSONObject, viewerId: Int, viewerName: String): YamiboPrivateMessage {
    val authorId = messagePositive(if (value.has("msgfromid")) value.opt("msgfromid") else value.opt("authorid"), "msgfromid")
    val recipientId = messagePositive(value.opt("touid"), "touid")
    val fallbackAuthor = value.messageString("author", "")
    val authorName = value.messageString("msgfrom", fallbackAuthor).trim().ifEmpty { if (authorId == viewerId) viewerName else "" }
    val recipientName = value.messageString("tousername", "").trim().ifEmpty { if (recipientId == viewerId) viewerName else "" }
    return YamiboPrivateMessage(
        messageUser(authorId, authorName),
        messageNonNegative(value.opt("dateline"), "dateline").toLong() * 1_000,
        value.messageString("vdateline", ""),
        messagePositive(value.opt("pmid"), "pmid"),
        authorId == viewerId,
        messageUser(recipientId, recipientName),
        messageText(value.messageString("message", "")),
    )
}

private fun parseMessagePagination(value: JSONObject): YamiboMessagePagination {
    val total = messageNonNegative(value.opt("count"), "count")
    val size = messagePositive(value.opt("perpage"), "perpage")
    val page = messagePositive(value.opt("page"), "page")
    val pages = ceil(total.toDouble() / size).toInt()
    return YamiboMessagePagination(page < pages, page, size, pages, total)
}

private fun validateMessageInput(page: Int, pageSize: Int) {
    require(page > 0) { "page must be a positive integer" }
    require(pageSize > 0) { "pageSize must be a positive integer" }
    require(pageSize <= 100) { "pageSize must not exceed 100" }
}

private fun messageUser(id: Int, name: String) = YamiboMessageUser(
    "$YAMIBO_ORIGIN/uc_server/avatar.php?uid=$id&size=small",
    id,
    name,
)

private fun JSONObject.messageString(key: String, fallback: String? = null): String {
    val raw = opt(key)
    if ((raw == null || raw == JSONObject.NULL) && fallback != null) return fallback
    return raw as? String ?: messageInvalid("百合会消息数据缺少有效的 $key 字段")
}

private fun messageInteger(raw: Any?, field: String): Int = when (raw) {
    is Int -> raw
    is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    is String -> raw.takeIf { Regex("""^-?\d+$""").matches(it) }?.toIntOrNull()
    else -> null
} ?: messageInvalid("百合会消息数据包含无效的 $field 字段")

private fun messagePositive(raw: Any?, field: String) = messageInteger(raw, field).takeIf { it > 0 }
    ?: messageInvalid("百合会消息数据包含无效的 $field 字段")
private fun messageNonNegative(raw: Any?, field: String) = messageInteger(raw, field).takeIf { it >= 0 }
    ?: messageInvalid("百合会消息数据包含无效的 $field 字段")
private fun messageOptionalPositive(raw: Any?, field: String): Int? {
    if (raw == null || raw == JSONObject.NULL || raw == "" || raw == "0" || raw == 0) return null
    return messagePositive(raw, field)
}

private fun messageText(value: String): String = decodeMessageEntities(
    value.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]*>"""), "").replace("\r", ""),
).replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n[ \t]+"""), "\n").trim()

private fun decodeMessageEntities(value: String): String = Regex("""&(?:#(\d+)|#x([\da-f]+)|([a-z]+));""", RegexOption.IGNORE_CASE)
    .replace(value) { match ->
        val decimal = match.groupValues[1]
        val hex = match.groupValues[2]
        if (decimal.isNotEmpty() || hex.isNotEmpty()) {
            runCatching { (if (decimal.isNotEmpty()) decimal.toInt() else hex.toInt(16)).let(Character::toChars).concatToString() }.getOrDefault(match.value)
        } else mapOf("amp" to "&", "apos" to "'", "gt" to ">", "lt" to "<", "nbsp" to "\u00a0", "quot" to "\"")[match.groupValues[3].lowercase()] ?: match.value
    }

private fun JSONArray.messageObjects(error: String): List<JSONObject> =
    (0 until length()).map { opt(it) as? JSONObject ?: messageInvalid(error) }
private fun messageInvalid(message: String): Nothing = throw YamiboMessageException(YamiboMessageErrorCode.INVALID_RESPONSE, message)
private fun YamiboApiErrorCode.toMessageCode() = when (this) {
    YamiboApiErrorCode.INVALID_RESPONSE -> YamiboMessageErrorCode.INVALID_RESPONSE
    YamiboApiErrorCode.NETWORK_ERROR -> YamiboMessageErrorCode.NETWORK_ERROR
    YamiboApiErrorCode.SERVER_ERROR -> YamiboMessageErrorCode.SERVER_ERROR
}
