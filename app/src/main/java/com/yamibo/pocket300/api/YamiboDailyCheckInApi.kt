package com.yamibo.pocket300.api

enum class YamiboDailyCheckInState { AVAILABLE, CHECKED_IN }

data class YamiboDailyCheckInStatus(
    val state: YamiboDailyCheckInState,
)

enum class YamiboDailyCheckInErrorCode {
    NOT_AUTHENTICATED,
    INVALID_RESPONSE,
    NETWORK_ERROR,
    SERVER_ERROR,
}

class YamiboDailyCheckInException(
    val code: YamiboDailyCheckInErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class YamiboDailyCheckInApi(private val client: YamiboClient) {
    suspend fun getStatus(): YamiboDailyCheckInStatus = checkInCall {
        requireSession()
        requestStatus().status
    }

    suspend fun checkIn(): YamiboDailyCheckInStatus = checkInCall {
        requireSession()
        val current = requestStatus()
        if (current.status.state == YamiboDailyCheckInState.CHECKED_IN) return@checkInCall current.status

        val token = current.signToken ?: checkInInvalid("百合会签到页缺少打卡校验值")
        val response = client.requestPage(
            "/plugin.php",
            mapOf(
                "id" to CHECK_IN_PLUGIN_ID,
                "mobile" to "2",
                "sign" to token,
            ),
        )
        val result = parseDailyCheckInPage(response.html, response.url)
        if (result.status.state != YamiboDailyCheckInState.CHECKED_IN) {
            throw YamiboDailyCheckInException(
                YamiboDailyCheckInErrorCode.SERVER_ERROR,
                "签到未完成，请稍后重试",
            )
        }
        result.status
    }

    private suspend fun requireSession() {
        val session = YamiboAuthApi(client).getCurrentSession()
        if (session == null) {
            throw YamiboDailyCheckInException(
                YamiboDailyCheckInErrorCode.NOT_AUTHENTICATED,
                "请先登录百合会",
            )
        }
    }

    private suspend fun requestStatus(): ParsedDailyCheckInPage {
        val response = client.requestPage(
            "/plugin.php",
            mapOf("id" to CHECK_IN_PLUGIN_ID, "mobile" to "2"),
        )
        return parseDailyCheckInPage(response.html, response.url)
    }

    private suspend fun <T> checkInCall(block: suspend () -> T): T = try {
        block()
    } catch (error: YamiboDailyCheckInException) {
        throw error
    } catch (error: YamiboAuthException) {
        throw YamiboDailyCheckInException(
            when (error.code) {
                YamiboAuthErrorCode.NOT_AUTHENTICATED -> YamiboDailyCheckInErrorCode.NOT_AUTHENTICATED
                YamiboAuthErrorCode.INVALID_RESPONSE -> YamiboDailyCheckInErrorCode.INVALID_RESPONSE
                YamiboAuthErrorCode.NETWORK_ERROR -> YamiboDailyCheckInErrorCode.NETWORK_ERROR
                else -> YamiboDailyCheckInErrorCode.SERVER_ERROR
            },
            error.message ?: "百合会请求失败",
            error,
        )
    } catch (error: YamiboApiException) {
        throw YamiboDailyCheckInException(
            when (error.code) {
                YamiboApiErrorCode.INVALID_RESPONSE -> YamiboDailyCheckInErrorCode.INVALID_RESPONSE
                YamiboApiErrorCode.NETWORK_ERROR -> YamiboDailyCheckInErrorCode.NETWORK_ERROR
                YamiboApiErrorCode.SERVER_ERROR -> YamiboDailyCheckInErrorCode.SERVER_ERROR
            },
            error.message ?: "百合会请求失败",
            error,
        )
    }
}

internal data class ParsedDailyCheckInPage(
    val status: YamiboDailyCheckInStatus,
    val signToken: String?,
)

internal fun parseDailyCheckInPage(html: String, responseUrl: String): ParsedDailyCheckInPage {
    if (isDailyCheckInLoginPage(html, responseUrl)) {
        throw YamiboDailyCheckInException(
            YamiboDailyCheckInErrorCode.NOT_AUTHENTICATED,
            "请先登录百合会",
        )
    }

    val button = Regex(
        """<div\b[^>]*class=["'][^"']*\bsignbtn\b[^"']*["'][^>]*>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1)
    if (button == null) {
        val message = Regex(
            """<div\b[^>]*\bid=["']messagetext["'][^>]*>[\s\S]*?<p\b[^>]*>([\s\S]*?)</p>""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.get(1)?.let(::dailyCheckInText)
        if (!message.isNullOrEmpty()) {
            throw YamiboDailyCheckInException(YamiboDailyCheckInErrorCode.SERVER_ERROR, message)
        }
        checkInInvalid("百合会返回了无法识别的签到页")
    }
    val buttonText = dailyCheckInText(button)

    if ("今日已打卡" in buttonText) {
        return ParsedDailyCheckInPage(
            YamiboDailyCheckInStatus(YamiboDailyCheckInState.CHECKED_IN),
            null,
        )
    }

    val token = Regex(
        """href=["'][^"']*\bsign=([A-Za-z0-9]+)[^"']*["'][^>]*>\s*点击打卡\s*</a>""",
        RegexOption.IGNORE_CASE,
    ).find(button)?.groupValues?.get(1)
        ?: checkInInvalid("百合会签到页缺少可用的打卡入口")
    return ParsedDailyCheckInPage(
        YamiboDailyCheckInStatus(YamiboDailyCheckInState.AVAILABLE),
        token,
    )
}

private fun isDailyCheckInLoginPage(html: String, responseUrl: String): Boolean =
    responseUrl.contains("member.php", ignoreCase = true) &&
        responseUrl.contains("mod=logging", ignoreCase = true) ||
        Regex("""<form\b[^>]*\bid=["']loginform""", RegexOption.IGNORE_CASE).containsMatchIn(html)

private fun dailyCheckInText(value: String): String = value
    .replace(Regex("""<[^>]+>"""), " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("""\s+"""), " ")
    .trim()

private fun checkInInvalid(message: String): Nothing = throw YamiboDailyCheckInException(
    YamiboDailyCheckInErrorCode.INVALID_RESPONSE,
    message,
)

private const val CHECK_IN_PLUGIN_ID = "zqlj_sign"
