package com.yamibo.pocket300.api

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YamiboAuthApiTest {
    @Test
    fun parsesAuthenticatedSession() {
        val response = DiscuzResponse(
            variables = JSONObject(
                """{"member_uid":"42","member_username":"alice","member_avatar":"a","formhash":"h","groupid":"10","readaccess":"20"}""",
            ),
            message = null,
            error = null,
            version = "4",
            charset = "UTF-8",
        )
        val session = requireNotNull(parseSession(response))
        assertEquals(42, session.uid)
        assertEquals("alice", session.username)
        assertEquals("h", session.formHash)
    }

    @Test
    fun treatsGuestAsNoSession() {
        val response = DiscuzResponse(JSONObject("""{"member_uid":"0","member_username":""}"""), null, null, null, null)
        assertNull(parseSession(response))
    }

    @Test
    fun parsesSecurityQuestions() {
        val html = """<select name="questionid"><option value="0">未设置</option><option value='2'>爷爷&amp;奶奶</option></select>"""
        assertEquals(
            listOf(SecurityQuestionOption(0, "未设置"), SecurityQuestionOption(2, "爷爷&奶奶")),
            parseSecurityQuestionsFromLoginPage(html),
        )
    }

    @Test
    fun parsesProfileAndNormalizesAvatar() {
        val html = """
            <title>Alice 的个人资料 - 百合会</title>
            <img src="/uc_server/avatar.php?uid=42&amp;size=small">
            <ul><li><em>注册时间：</em>2020-01-01</li></ul>
        """.trimIndent()
        val profile = parseUserProfilePage(html, "$YAMIBO_ORIGIN/home.php?uid=42", 42)
        assertEquals("Alice", profile.displayName)
        assertEquals("$YAMIBO_ORIGIN/uc_server/avatar.php?uid=42&size=small", profile.avatarUrl)
        assertEquals(listOf(YamiboProfileField("注册时间", "2020-01-01")), profile.fields)
    }

    @Test
    fun parsesStaticProfileAvatarAndPreservesCacheVersion() {
        val html = """
            <img src="/uc_server/avatar.php?uid=42&amp;size=small">
            <img src="/uc_server/data/avatar/000/59/42/15_avatar_small.jpg?ts=1742136902&amp;v=2" class="user_avatar">
        """.trimIndent()
        val profile = parseUserProfilePage(html, "$YAMIBO_ORIGIN/home.php?uid=594215", 594215)
        assertEquals(
            "$YAMIBO_ORIGIN/uc_server/data/avatar/000/59/42/15_avatar_small.jpg?ts=1742136902&v=2",
            profile.avatarUrl,
        )
    }

    @Test(expected = YamiboApiException::class)
    fun rejectsLoginPageAsProfile() {
        parseUserProfilePage("<form id='loginform'>", "$YAMIBO_ORIGIN/member.php?mod=logging", 42)
    }

    @Test
    fun detectsValidDiscuzAuthCookiesAndRejectsDeletedOrBlankValues() {
        val domain = "bbs.yamibo.com"
        assertTrue(
            hasDiscuzAuthCookie(
                listOf(Cookie.Builder().domain(domain).name("EeqY_2132_auth").value("token").build()),
            ),
        )
        assertTrue(
            hasDiscuzAuthCookie(
                listOf(Cookie.Builder().domain(domain).name("auth").value("token").build()),
            ),
        )
        assertFalse(
            hasDiscuzAuthCookie(
                listOf(Cookie.Builder().domain(domain).name("EeqY_2132_auth").value("deleted").build()),
            ),
        )
        assertFalse(
            hasDiscuzAuthCookie(
                listOf(Cookie.Builder().domain(domain).name("EeqY_2132_auth").value("").build()),
            ),
        )
        assertFalse(
            hasDiscuzAuthCookie(
                listOf(Cookie.Builder().domain(domain).name("EeqY_2132_saltkey").value("abc").build()),
            ),
        )
        assertFalse(hasDiscuzAuthCookie(emptyList()))
    }

    @Test
    fun seedsAndUpdatesClientAuthenticationStateFromSessionResponses() = runBlocking {
        val cookieClient = YamiboClient(
            cookieJar = object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
                override fun loadForRequest(url: HttpUrl): List<Cookie> = listOf(
                    Cookie.Builder().domain("bbs.yamibo.com").name("EeqY_2132_auth").value("token").build(),
                )
            },
        )
        assertTrue(cookieClient.isAuthenticated.value)

        val responses = ArrayDeque(
            listOf(
                """{"Version":"4","Variables":{"member_uid":"42","member_username":"alice"}}""",
                """{"Version":"4","Variables":{"member_uid":"0","member_username":""}}""",
            ),
        )
        val guestClient = YamiboClient(
            cookieJar = CookieJar.NO_COOKIES,
            callFactory = stubCallFactory { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("HTTP 200")
                    .body(responses.removeFirst().toResponseBody("application/json".toMediaType()))
                    .build()
            },
        )
        assertFalse(guestClient.isAuthenticated.value)

        guestClient.requestMobileApi(mapOf("module" to "login"))
        assertTrue(guestClient.isAuthenticated.value)

        guestClient.requestMobileApi(mapOf("module" to "login"))
        assertFalse(guestClient.isAuthenticated.value)
    }

    private fun stubCallFactory(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain -> handler(chain.request()) }
            .build()
}
