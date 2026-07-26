package com.yamibo.pocket300.api

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Pocket300UserAgentTest {
    @Test
    fun usesConfiguredAndroidEdgeUserAgent() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 13; SM-G981B) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Edg/152.0.0.0 " +
                "Mobile Safari/537.36",
            POCKET300_USER_AGENT,
        )
        assertTrue(POCKET300_USER_AGENT.all { it.code in 0x20..0x7E })
    }

    @Test
    fun requestUserAgentOverridesLibraryAndCallerDefaults() {
        val request = Request.Builder()
            .url(YAMIBO_ORIGIN)
            .header("User-Agent", "okhttp/5.4.0")
            .header("Accept-Language", "zh-CN")
            .build()

        val updated = request.withUserAgent(POCKET300_USER_AGENT)

        assertEquals(POCKET300_USER_AGENT, updated.header("User-Agent"))
        assertEquals("zh-CN", updated.header("Accept-Language"))
    }
}
