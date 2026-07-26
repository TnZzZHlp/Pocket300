package com.yamibo.pocket300.api

import com.yamibo.pocket300.logging.AppLogger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

internal class Pocket300UserAgentInterceptor(
    private val userAgent: String,
) : Interceptor {
    private val hasLoggedUserAgent = AtomicBoolean()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().withUserAgent(userAgent)
        if (hasLoggedUserAgent.compareAndSet(false, true)) {
            AppLogger.debug(TAG) { "Sending requests with User-Agent: $userAgent" }
        }
        return chain.proceed(request)
    }

    private companion object {
        const val TAG = "YamiboClient"
    }
}

internal fun Request.withUserAgent(userAgent: String): Request = newBuilder()
    .header(USER_AGENT_HEADER, userAgent)
    .build()

private const val USER_AGENT_HEADER = "User-Agent"
internal const val POCKET300_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; SM-G981B) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Edg/152.0.0.0 Mobile Safari/537.36"
