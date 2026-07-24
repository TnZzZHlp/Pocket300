package com.yamibo.pocket300.api

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

const val YAMIBO_ORIGIN = "https://bbs.yamibo.com"

enum class YamiboApiErrorCode { INVALID_RESPONSE, NETWORK_ERROR, SERVER_ERROR }

class YamiboApiException(
    val code: YamiboApiErrorCode,
    message: String,
    val serverCode: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

data class DiscuzMessage(val message: String, val code: String)

data class DiscuzResponse(
    val variables: JSONObject?,
    val message: DiscuzMessage?,
    val error: String?,
    val version: String?,
    val charset: String?,
)

data class YamiboPageResponse(val html: String, val url: String)

/**
 * Shared Yamibo transport. A single cookie jar is deliberately retained for the
 * lifetime of the client because Discuz authentication is held in HttpOnly cookies.
 */
class YamiboClient(
    cookieJar: CookieJar = AndroidCookieJar(),
    timeoutMillis: Long = 15_000,
) {
    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    suspend fun requestMobileApi(
        parameters: Map<String, String>,
        form: Map<String, String>? = null,
    ): DiscuzResponse = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("bbs.yamibo.com")
            .addPathSegments("api/mobile/index.php")
            .addQueryParameter("version", "4")
            .apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder().url(url).apply {
            if (form != null) {
                val body = FormBody.Builder().apply {
                    form.forEach { (key, value) -> add(key, value) }
                }.build()
                post(body)
            }
        }.build()

        val text = execute(request)
        val root = try {
            JSONObject(text.body)
        } catch (error: JSONException) {
            throw YamiboApiException(
                YamiboApiErrorCode.INVALID_RESPONSE,
                "百合会返回了无法解析的数据",
                cause = error,
            )
        }
        val variables = root.objectOrNull("Variables")
        if (root.has("Variables") && variables == null) {
            throw YamiboApiException(YamiboApiErrorCode.INVALID_RESPONSE, "百合会返回了无法识别的数据")
        }
        val messageObject = root.objectOrNull("Message")
        DiscuzResponse(
            variables = variables,
            message = messageObject?.let {
                DiscuzMessage(it.stringOrEmpty("messagestr"), it.stringOrEmpty("messageval"))
            },
            error = root.nonBlankStringOrNull("error"),
            version = root.stringOrNull("Version"),
            charset = root.stringOrNull("Charset"),
        )
    }

    suspend fun requestPage(
        path: String,
        parameters: Map<String, String> = emptyMap(),
        form: Map<String, String>? = null,
    ): YamiboPageResponse = requestPageInternal(
        path = path,
        parameters = parameters,
        formFields = form?.map { (key, value) -> key to value },
    )

    suspend fun requestPage(
        path: String,
        parameters: Map<String, String>,
        formFields: List<Pair<String, String>>,
    ): YamiboPageResponse = requestPageInternal(path, parameters, formFields)

    private suspend fun requestPageInternal(
        path: String,
        parameters: Map<String, String>,
        formFields: List<Pair<String, String>>?,
    ): YamiboPageResponse = withContext(Dispatchers.IO) {
        require(path.startsWith('/') && !path.startsWith("//")) {
            "path must be an absolute Yamibo path"
        }
        val base = "$YAMIBO_ORIGIN$path".toHttpUrl()
        val url = base.newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).apply {
            if (formFields != null) {
                val body = FormBody.Builder().apply {
                    formFields.forEach { (key, value) -> add(key, value) }
                }.build()
                post(body)
            }
        }.build()
        val response = execute(request)
        YamiboPageResponse(response.body, response.url)
    }

    private fun execute(request: Request): TextResponse {
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw YamiboApiException(
                        YamiboApiErrorCode.SERVER_ERROR,
                        "百合会请求失败（HTTP ${response.code}）",
                    )
                }
                val body = try {
                    response.body.string()
                } catch (error: IOException) {
                    throw YamiboApiException(
                        YamiboApiErrorCode.INVALID_RESPONSE,
                        "百合会返回了无法读取的数据",
                        cause = error,
                    )
                }
                return TextResponse(body, response.request.url.toString())
            }
        } catch (error: YamiboApiException) {
            throw error
        } catch (error: IOException) {
            val timedOut = error is java.net.SocketTimeoutException || error is java.io.InterruptedIOException
            throw YamiboApiException(
                YamiboApiErrorCode.NETWORK_ERROR,
                if (timedOut) "连接百合会超时" else "无法连接百合会",
                cause = error,
            )
        }
    }

    private data class TextResponse(val body: String, val url: String)
}

/**
 * Bridges OkHttp to Android's persistent cookie store. This preserves Discuz
 * login across API modules and app process restarts, including HttpOnly cookies.
 */
class AndroidCookieJar(
    private val manager: CookieManager = CookieManager.getInstance(),
) : CookieJar {
    init {
        manager.setAcceptCookie(true)
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { manager.setCookie(url.toString(), it.toString(), null) }
        manager.flush()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = manager.getCookie(url.toString())
        ?.split(';')
        ?.mapNotNull { Cookie.parse(url, it.trim()) }
        .orEmpty()
}

/** Thread-safe process-lifetime cookie storage, including HttpOnly authentication cookies. */
class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.removeAll { stored ->
            stored.expiresAt < now || cookies.any { incoming ->
                incoming.name == stored.name &&
                    incoming.domain == stored.domain &&
                    incoming.path == stored.path
            }
        }
        this.cookies += cookies.filter { it.expiresAt >= now }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = cookies.clear()
}

internal fun JSONObject.objectOrNull(key: String): JSONObject? =
    if (!has(key) || isNull(key)) null else opt(key) as? JSONObject

internal fun JSONObject.arrayOrNull(key: String): JSONArray? =
    if (!has(key) || isNull(key)) null else opt(key) as? JSONArray

internal fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else opt(key) as? String

internal fun JSONObject.stringOrEmpty(key: String): String = stringOrNull(key).orEmpty()

internal fun JSONObject.nonBlankStringOrNull(key: String): String? =
    stringOrNull(key)?.takeIf(String::isNotBlank)

internal fun invalidResponse(message: String): Nothing =
    throw YamiboApiException(YamiboApiErrorCode.INVALID_RESPONSE, message)

internal fun JSONObject.requiredString(key: String, context: String): String =
    stringOrNull(key) ?: invalidResponse("$context 缺少有效的 $key 字段")

internal fun JSONObject.positiveInt(key: String, context: String): Int {
    val value = requiredString(key, context).toIntOrNull()
    if (value == null || value <= 0) invalidResponse("$context 包含无效的 $key 字段")
    return value
}

internal fun JSONObject.nonNegativeInt(key: String, context: String, fallback: Int = 0): Int {
    val raw = stringOrNull(key) ?: return fallback
    val value = raw.toIntOrNull()
    if (value == null || value < 0) invalidResponse("$context 包含无效的 $key 字段")
    return value
}
