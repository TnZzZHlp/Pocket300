package com.yamibo.pocket300.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test(expected = YamiboAuthException::class)
    fun rejectsLoginPageAsProfile() {
        parseUserProfilePage("<form id='loginform'>", "$YAMIBO_ORIGIN/member.php?mod=logging", 42)
    }
}
