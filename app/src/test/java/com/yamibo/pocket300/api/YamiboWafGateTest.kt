package com.yamibo.pocket300.api

import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YamiboWafGateTest {
    @Test
    fun recognizesNonBlankWafCookie() {
        assertTrue(hasYamiboWafCookie("sid=abc; nox_jst_v1=challenge-token; theme=light"))
    }

    @Test
    fun rejectsMissingEmptyOrSimilarCookieNames() {
        assertFalse(hasYamiboWafCookie(null))
        assertFalse(hasYamiboWafCookie("sid=abc; nox_jst_v1=; theme=light"))
        assertFalse(hasYamiboWafCookie("sid=abc; nox_jst_v10=challenge-token"))
    }

    @Test
    fun detectsFreshWafCookieOnlyWhenValueChangesOrWasMissing() {
        assertTrue(hasFreshYamiboWafCookie(null, "nox_jst_v1=new-token"))
        assertTrue(hasFreshYamiboWafCookie("old-token", "sid=1; nox_jst_v1=new-token"))
        assertFalse(hasFreshYamiboWafCookie("old-token", "sid=1; nox_jst_v1=old-token"))
        assertFalse(hasFreshYamiboWafCookie("old-token", "sid=1; nox_jst_v1="))
        assertFalse(hasFreshYamiboWafCookie(null, null))
    }

    @Test
    fun parsesWebViewVerificationEvalResult() {
        assertTrue(parseWebViewVerificationEvalResult("\"discuz_ready\""))
        assertTrue(parseWebViewVerificationEvalResult("discuz_ready"))
        assertFalse(parseWebViewVerificationEvalResult("\"loading\""))
        assertFalse(parseWebViewVerificationEvalResult("\"challenge\""))
        assertFalse(parseWebViewVerificationEvalResult("\"pending\""))
        assertFalse(parseWebViewVerificationEvalResult(null))
    }

    @Test
    fun distinguishesNormalResponsesFromBrowserChallenges() {
        assertFalse(
            isYamiboBrowserChallenge(
                statusCode = 200,
                contentType = "application/json; charset=utf-8",
                body = """{"Version":"4","Variables":{}}""",
                expectJson = true,
            ),
        )
        assertFalse(
            isYamiboBrowserChallenge(
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                body = "<!DOCTYPE html><html><title>论坛 - Powered by Discuz!</title></html>",
                expectJson = false,
            ),
        )
        assertFalse(
            isYamiboBrowserChallenge(
                statusCode = 200,
                contentType = "text/xml; charset=utf-8",
                body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><root><![CDATA[ok]]></root>",
                expectJson = false,
            ),
        )
        assertFalse(
            isYamiboBrowserChallenge(
                statusCode = 500,
                contentType = "text/plain",
                body = "Internal Server Error",
                expectJson = true,
            ),
        )

        assertTrue(
            isYamiboBrowserChallenge(
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                body = "<html><script>/* nox_jst challenge */</script></html>",
                expectJson = true,
            ),
        )
        assertTrue(
            isYamiboBrowserChallenge(
                statusCode = 403,
                contentType = "text/html; charset=utf-8",
                body = "<html><body>bdwaf forbidden</body></html>",
                expectJson = true,
            ),
        )
        assertTrue(
            isYamiboBrowserChallenge(
                statusCode = 503,
                contentType = "text/html",
                body = "<!DOCTYPE html><html><body>Checking your browser</body></html>",
                expectJson = false,
            ),
        )
        assertTrue(
            isYamiboBrowserChallenge(
                statusCode = 200,
                contentType = "text/html; charset=utf-8",
                body = "<html><body>请先完成安全验证</body></html>",
                expectJson = false,
            ),
        )
    }

    @Test
    fun directRequestsBypassGateWhenNoChallengeIsReturned() = runBlocking {
        var gateCalls = 0
        val client = YamiboClient(
            cookieJar = CookieJar.NO_COOKIES,
            requestGate = YamiboRequestGate {
                gateCalls++
                throw YamiboWafException("gate should not be invoked")
            },
            callFactory = stubCallFactory { request ->
                if (request.url.encodedPath == "/api/mobile/index.php") {
                    httpResponse(request, 200, "application/json", """{"Version":"4","Variables":{}}""")
                } else {
                    httpResponse(
                        request,
                        200,
                        "text/html",
                        "<html><title>百合会 - Powered by Discuz!</title></html>",
                    )
                }
            },
        )

        val apiResult = client.requestMobileApi(mapOf("module" to "forumindex"))
        val pageResult = client.requestPage("/")

        assertEquals("4", apiResult.version)
        assertTrue(pageResult.html.contains("Powered by Discuz!"))
        assertEquals(0, gateCalls)
    }

    @Test
    fun challengedRequestRunsGateOnceAndRetries() = runBlocking {
        var attempts = 0
        var gateCalls = 0
        val client = YamiboClient(
            cookieJar = CookieJar.NO_COOKIES,
            requestGate = YamiboRequestGate { gateCalls++ },
            callFactory = stubCallFactory { request ->
                attempts++
                if (attempts == 1) {
                    httpResponse(
                        request,
                        403,
                        "text/html",
                        "<html><body>nox_jst browser verification</body></html>",
                    )
                } else {
                    httpResponse(request, 200, "application/json", """{"Version":"4","Variables":{}}""")
                }
            },
        )

        val result = client.requestMobileApi(mapOf("module" to "forumindex"))

        assertEquals("4", result.version)
        assertEquals(1, gateCalls)
        assertEquals(2, attempts)
    }

    @Test
    fun failsWithBrowserVerificationErrorWhenRetryRemainsChallenged() = runBlocking {
        var gateCalls = 0
        val client = YamiboClient(
            cookieJar = CookieJar.NO_COOKIES,
            requestGate = YamiboRequestGate { gateCalls++ },
            callFactory = stubCallFactory { request ->
                httpResponse(
                    request,
                    503,
                    "text/html",
                    "<html><body>正在验证浏览器</body></html>",
                )
            },
        )

        try {
            client.requestPage("/")
            fail("Expected YamiboApiException")
        } catch (error: YamiboApiException) {
            assertEquals("browser_verification", error.serverCode)
            assertEquals(1, gateCalls)
        }
    }

    private fun stubCallFactory(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain -> handler(chain.request()) }
            .build()

    private fun httpResponse(
        request: okhttp3.Request,
        code: Int,
        mimeType: String,
        body: String,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("HTTP $code")
        .body(body.toResponseBody(mimeType.toMediaType()))
        .build()
}
