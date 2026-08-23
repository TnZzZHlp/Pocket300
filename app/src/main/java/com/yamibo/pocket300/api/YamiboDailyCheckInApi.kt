package com.yamibo.pocket300.api

import com.yamibo.pocket300.logging.AppLogger

enum class YamiboDailyCheckInState { AVAILABLE, CHECKED_IN }

data class YamiboDailyCheckInStatus(
    val state: YamiboDailyCheckInState,
)

class YamiboDailyCheckInApi(private val client: YamiboClient) {
    suspend fun getStatus(): YamiboDailyCheckInStatus {
        requireSession()
        return requestStatus().status
    }

    suspend fun checkIn(): YamiboDailyCheckInStatus {
        requireSession()
        val current = requestStatus()
        if (current.status.state == YamiboDailyCheckInState.CHECKED_IN) {
            AppLogger.debug(TAG) { "Daily check-in skipped because it is already complete" }
            return current.status
        }

        val token = current.signToken ?: checkInInvalid("百合会签到页缺少打卡校验值")
        val response = client.requestPage(
            "/plugin.php",
            mapOf(
                "id" to CHECK_IN_PLUGIN_ID,
                "mobile" to "2",
                "sign" to token,
            ),
        )
        val result = parseDailyCheckInSubmission(response.html, response.url)
        if (result.status.state != YamiboDailyCheckInState.CHECKED_IN) {
            throw YamiboApiException(YamiboApiErrorCode.SERVER_ERROR, "签到未完成，请稍后重试")
        }
        AppLogger.info(TAG) { "Daily check-in completed successfully" }
        return result.status
    }

    private suspend fun requireSession() {
        if (YamiboAuthApi(client).getCurrentSession() == null) {
            throw YamiboApiException(YamiboApiErrorCode.SERVER_ERROR, "请先登录百合会", "not_authenticated")
        }
    }

    private suspend fun requestStatus(): ParsedDailyCheckInPage {
        val response = client.requestPage(
            "/plugin.php",
            mapOf("id" to CHECK_IN_PLUGIN_ID, "mobile" to "2"),
        )
        return parseDailyCheckInPage(response.html, response.url)
    }

    private companion object {
        const val TAG = "DailyCheckIn"
    }
}

internal data class ParsedDailyCheckInPage(
    val status: YamiboDailyCheckInStatus,
    val signToken: String?,
)

internal fun parseDailyCheckInSubmission(html: String, responseUrl: String): ParsedDailyCheckInPage {
    if (!isDailyCheckInLoginPage(html, responseUrl)) {
        val message = dailyCheckInText(html)
        if (CHECK_IN_SUCCESS_MESSAGES.any { it in message }) {
            return ParsedDailyCheckInPage(
                YamiboDailyCheckInStatus(YamiboDailyCheckInState.CHECKED_IN),
                null,
            )
        }
    }
    return parseDailyCheckInPage(html, responseUrl)
}

internal fun parseDailyCheckInPage(html: String, responseUrl: String): ParsedDailyCheckInPage {
    if (isDailyCheckInLoginPage(html, responseUrl)) {
        throw YamiboApiException(YamiboApiErrorCode.SERVER_ERROR, "请先登录百合会", "not_authenticated")
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
            throw YamiboApiException(YamiboApiErrorCode.SERVER_ERROR, message)
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

private fun checkInInvalid(message: String): Nothing =
    throw YamiboApiException(YamiboApiErrorCode.INVALID_RESPONSE, message)

private const val CHECK_IN_PLUGIN_ID = "zqlj_sign"
private val CHECK_IN_SUCCESS_MESSAGES = listOf(
    "恭喜您，打卡成功",
    "您今天已经打过卡了",
)
