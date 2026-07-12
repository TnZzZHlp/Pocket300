package com.yamibo.pocket300.api

import java.net.URI

data class SecurityQuestionOption(val id: Int, val label: String)

val DEFAULT_SECURITY_QUESTIONS: List<SecurityQuestionOption> = listOf(
    SecurityQuestionOption(0, "安全提问(未设置请忽略)"),
    SecurityQuestionOption(1, "母亲的名字"),
    SecurityQuestionOption(2, "爷爷的名字"),
    SecurityQuestionOption(3, "父亲出生的城市"),
    SecurityQuestionOption(4, "您其中一位老师的名字"),
    SecurityQuestionOption(5, "您个人计算机的型号"),
    SecurityQuestionOption(6, "您最喜欢的餐馆名称"),
    SecurityQuestionOption(7, "驾驶执照最后四位数字"),
)

data class YamiboSession(
    val avatarUrl: String,
    val formHash: String,
    val groupId: Int,
    val readAccess: Int,
    val uid: Int,
    val username: String,
)

data class LoginInput(
    val account: String,
    val password: String,
    val securityAnswer: String = "",
    val securityQuestionId: Int = 0,
)

data class GetUserProfileInput(val uid: Int)

data class YamiboProfileField(val label: String, val value: String)

data class YamiboUserProfile(
    val avatarUrl: String,
    val displayName: String?,
    val fields: List<YamiboProfileField>,
    val profileUrl: String,
    val uid: Int,
)

enum class YamiboAuthErrorCode {
    INVALID_CREDENTIALS,
    SECURITY_ANSWER_REQUIRED,
    SECURITY_ANSWER_INVALID,
    VERIFICATION_REQUIRED,
    TOO_MANY_ATTEMPTS,
    NOT_AUTHENTICATED,
    INVALID_RESPONSE,
    NETWORK_ERROR,
    SERVER_ERROR,
}

class YamiboAuthException(
    val code: YamiboAuthErrorCode,
    message: String,
    val serverCode: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class YamiboAuthApi(private val client: YamiboClient) {
    suspend fun login(input: LoginInput): YamiboSession {
        val account = input.account.trim()
        if (account.isEmpty() || input.password.isEmpty()) {
            throw YamiboAuthException(YamiboAuthErrorCode.INVALID_CREDENTIALS, "请输入账号和密码")
        }
        require(input.securityQuestionId in 0..7) {
            "securityQuestionId must be an integer from 0 to 7"
        }

        val response = request(
            parameters = mapOf(
                "loginfield" to "auto",
                "loginsubmit" to "yes",
                "module" to "login",
            ),
            form = mapOf(
                "answer" to input.securityAnswer,
                "cookietime" to "2592000",
                "loginsubmit" to "yes",
                "password" to input.password,
                "questionid" to input.securityQuestionId.toString(),
                "username" to account,
            ),
        )
        return parseSession(response) ?: throwResponseError(response)
    }

    suspend fun getLoginSecurityQuestions(): List<SecurityQuestionOption> = authCall {
        val response = client.requestPage(
            "/member.php",
            mapOf("action" to "login", "mobile" to "2", "mod" to "logging"),
        )
        parseSecurityQuestionsFromLoginPage(response.html).ifEmpty { DEFAULT_SECURITY_QUESTIONS }
    }

    suspend fun getUserProfile(uid: Int): YamiboUserProfile {
        require(uid > 0) { "uid must be a positive integer" }
        return authCall {
            val response = client.requestPage(
                "/home.php",
                mapOf(
                    "do" to "profile",
                    "mobile" to "2",
                    "mod" to "space",
                    "mycenter" to "1",
                    "uid" to uid.toString(),
                ),
            )
            parseUserProfilePage(response.html, response.url, uid)
        }
    }

    suspend fun getUserProfile(input: GetUserProfileInput): YamiboUserProfile = getUserProfile(input.uid)

    suspend fun getCurrentSession(): YamiboSession? = parseSession(request(mapOf("module" to "login")))

    suspend fun logout() {
        val session = getCurrentSession() ?: return
        if (session.formHash.isEmpty()) {
            throw YamiboAuthException(
                YamiboAuthErrorCode.INVALID_RESPONSE,
                "百合会未返回退出登录所需的校验值",
            )
        }
        request(mapOf("hash" to session.formHash, "mlogout" to "1", "module" to "login"))
        if (getCurrentSession() != null) {
            throw YamiboAuthException(YamiboAuthErrorCode.SERVER_ERROR, "退出登录失败")
        }
    }

    private suspend fun request(
        parameters: Map<String, String>,
        form: Map<String, String>? = null,
    ): DiscuzResponse = authCall { client.requestMobileApi(parameters, form) }

    private suspend fun <T> authCall(block: suspend () -> T): T = try {
        block()
    } catch (error: YamiboApiException) {
        throw YamiboAuthException(
            error.code.toAuthCode(),
            error.message ?: "百合会请求失败",
            error.serverCode,
            error,
        )
    }
}

fun parseSession(response: DiscuzResponse): YamiboSession? {
    val variables = response.variables ?: return null
    val uid = variables.stringOrNull("member_uid")?.toIntOrNull() ?: return null
    val username = variables.stringOrNull("member_username").orEmpty()
    if (uid <= 0 || username.isEmpty()) return null
    return YamiboSession(
        avatarUrl = variables.stringOrNull("member_avatar").orEmpty(),
        formHash = variables.stringOrNull("formhash").orEmpty(),
        groupId = variables.stringOrNull("groupid")?.toIntOrNull() ?: 0,
        readAccess = variables.stringOrNull("readaccess")?.toIntOrNull() ?: 0,
        uid = uid,
        username = username,
    )
}

fun parseSecurityQuestionsFromLoginPage(html: String): List<SecurityQuestionOption> {
    val select = Regex(
        """<select\b[^>]*\bname=["']questionid["'][^>]*>([\s\S]*?)</select>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return emptyList()
    return Regex(
        """<option\b[^>]*\bvalue=["']?(\d+)["']?[^>]*>([\s\S]*?)</option>""",
        setOf(RegexOption.IGNORE_CASE),
    ).findAll(select).mapNotNull { match ->
        val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        val label = decodeHtmlText(match.groupValues[2].replace(HTML_TAG, ""))
        if (id >= 0 && label.isNotEmpty()) SecurityQuestionOption(id, label) else null
    }.toList()
}

fun parseUserProfilePage(html: String, responseUrl: String, uid: Int): YamiboUserProfile {
    if (isLoginPage(html, responseUrl)) {
        throw YamiboAuthException(YamiboAuthErrorCode.NOT_AUTHENTICATED, "请先登录百合会")
    }
    return YamiboUserProfile(
        avatarUrl = parseProfileAvatarUrl(html, uid),
        displayName = parseProfileDisplayName(html),
        fields = parseProfileFields(html),
        profileUrl = responseUrl,
        uid = uid,
    )
}

private fun throwResponseError(response: DiscuzResponse): Nothing {
    val serverCode = response.message?.code?.takeIf(String::isNotEmpty)
        ?: response.error
        ?: "unknown_error"
    val (code, message) = when {
        serverCode == "login_invalid" -> YamiboAuthErrorCode.INVALID_CREDENTIALS to "账号或密码错误"
        serverCode == "login_question_empty" -> YamiboAuthErrorCode.SECURITY_ANSWER_REQUIRED to "请输入安全提问答案"
        serverCode == "login_question_invalid" -> YamiboAuthErrorCode.SECURITY_ANSWER_INVALID to "安全提问答案错误"
        serverCode == "login_strike" -> YamiboAuthErrorCode.TOO_MANY_ATTEMPTS to "登录失败次数过多，请稍后再试"
        serverCode == "login_seccheck2" || "seccode" in serverCode || "secqaa" in serverCode ->
            YamiboAuthErrorCode.VERIFICATION_REQUIRED to "网站要求进行额外安全验证"
        else -> YamiboAuthErrorCode.SERVER_ERROR to "百合会登录服务返回了错误"
    }
    throw YamiboAuthException(code, message, serverCode)
}

private fun YamiboApiErrorCode.toAuthCode(): YamiboAuthErrorCode = when (this) {
    YamiboApiErrorCode.INVALID_RESPONSE -> YamiboAuthErrorCode.INVALID_RESPONSE
    YamiboApiErrorCode.NETWORK_ERROR -> YamiboAuthErrorCode.NETWORK_ERROR
    YamiboApiErrorCode.SERVER_ERROR -> YamiboAuthErrorCode.SERVER_ERROR
}

private fun decodeHtmlText(value: String): String = value
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("""&#(\d+);""")) { match ->
        match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
    }
    .replace(Regex("""\s+"""), " ")
    .trim()

private fun htmlText(value: String): String = decodeHtmlText(
    value.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(HTML_TAG, "")
        .replace("\r", ""),
).replace(Regex("""[ \t]+\n"""), "\n")
    .replace(Regex("""\n[ \t]+"""), "\n")
    .replace(Regex("""\s+"""), " ")
    .trim()

private fun isLoginPage(html: String, responseUrl: String): Boolean {
    val uriMatch = runCatching {
        val uri = URI(responseUrl)
        uri.path.endsWith("/member.php") && uri.query.orEmpty().split('&').any { it == "mod=logging" }
    }.getOrDefault(false)
    return uriMatch || Regex("""\bpg_logging\b""", RegexOption.IGNORE_CASE).containsMatchIn(html) ||
        Regex("""<form\b[^>]*\bid=["']loginform["']""", RegexOption.IGNORE_CASE).containsMatchIn(html)
}

private fun parseProfileFields(html: String): List<YamiboProfileField> {
    val patterns = listOf(
        """<li\b[^>]*>\s*<em\b[^>]*>([\s\S]*?)</em>\s*([\s\S]*?)</li>""",
        """<li\b[^>]*>\s*<span\b[^>]*>([\s\S]*?)</span>\s*([\s\S]*?)</li>""",
        """<p\b[^>]*>\s*<em\b[^>]*>([\s\S]*?)</em>\s*([\s\S]*?)</p>""",
        """<tr\b[^>]*>\s*<th\b[^>]*>([\s\S]*?)</th>\s*<td\b[^>]*>([\s\S]*?)</td>\s*</tr>""",
        """<tr\b[^>]*>\s*<td\b[^>]*>([\s\S]*?)</td>\s*<td\b[^>]*>([\s\S]*?)</td>\s*</tr>""",
    )
    val ignored = setOf("首页", "收藏", "消息", "我的", "登录", "确定", "不用了")
    val seen = mutableSetOf<Pair<String, String>>()
    return patterns.flatMap { pattern ->
        Regex(pattern, RegexOption.IGNORE_CASE).findAll(html).mapNotNull { match ->
            val label = htmlText(match.groupValues[1]).replace(Regex("""[：:]\s*$"""), "").trim()
            val value = htmlText(match.groupValues[2])
            val pair = label to value
            if (label.isEmpty() || value.isEmpty() || label == value || label.length > 32 ||
                value.length > 160 || label in ignored || !seen.add(pair)
            ) null else YamiboProfileField(label, value)
        }.toList()
    }
}

private fun parseProfileDisplayName(html: String): String? {
    val patterns = listOf(
        """<div\b[^>]*class=["'][^"']*\b(?:user|profile|space)[^"']*(?:name|info|hd|header)[^"']*["'][^>]*>[\s\S]*?<h[123]\b[^>]*>([\s\S]*?)</h[123]>""",
        """<h[123]\b[^>]*>([\s\S]*?)(?:的个人资料|个人资料)?</h[123]>""",
        """<title>([\s\S]*?)</title>""",
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        val raw = Regex(pattern, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?: return@firstNotNullOfOrNull null
        htmlText(raw)
            .replace(Regex("""\s*-\s*百合会[\s\S]*$"""), "")
            .replace(Regex("""\s*的个人资料\s*$"""), "")
            .replace(Regex("""^个人资料\s*[-_]\s*"""), "")
            .trim()
            .takeIf { it.isNotEmpty() && it != "登录" }
    }
}

private fun parseProfileAvatarUrl(html: String, uid: Int): String {
    val uidPattern = Regex.escape(uid.toString())
    val exact = Regex(
        """<img\b[^>]*\bsrc=["']([^"']*avatar\.php\?[^"']*uid=$uidPattern[^"']*)["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1)
    val any = exact ?: Regex(
        """<img\b[^>]*\bsrc=["']([^"']*avatar\.php\?[^"']*)["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1)
    return any?.let(::normalizeSiteUrl) ?: "$YAMIBO_ORIGIN/uc_server/avatar.php?uid=$uid&size=small"
}

private fun normalizeSiteUrl(raw: String): String {
    val value = decodeHtmlText(raw.trim())
    if (value.startsWith("//")) return "https:$value"
    return URI("$YAMIBO_ORIGIN/").resolve(value).toString()
}

private val HTML_TAG = Regex("""<[^>]*>""")
