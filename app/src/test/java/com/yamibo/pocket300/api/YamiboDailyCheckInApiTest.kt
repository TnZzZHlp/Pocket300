package com.yamibo.pocket300.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class YamiboDailyCheckInApiTest {
    @Test
    fun parsesAvailableCheckInAndToken() {
        val page = parseDailyCheckInPage(
            """
                <div class="signbtn cl">
                    <a href="plugin.php?id=zqlj_sign&amp;sign=abc123ef" class="btna">点击打卡</a>
                </div>
            """.trimIndent(),
            "$YAMIBO_ORIGIN/plugin.php?id=zqlj_sign",
        )

        assertEquals(YamiboDailyCheckInState.AVAILABLE, page.status.state)
        assertEquals("abc123ef", page.signToken)
    }

    @Test
    fun parsesCompletedCheckIn() {
        val page = parseDailyCheckInPage(
            """<div class='signbtn cl'><span class='btna'>今日已打卡</span></div>""",
            "$YAMIBO_ORIGIN/plugin.php?id=zqlj_sign",
        )

        assertEquals(YamiboDailyCheckInState.CHECKED_IN, page.status.state)
        assertNull(page.signToken)
    }

    @Test
    fun parsesSuccessfulCheckInSubmissionMessage() {
        val page = parseDailyCheckInSubmission(
            """<div id="messagetext"><p>恭喜您，打卡成功！</p></div>""",
            "$YAMIBO_ORIGIN/plugin.php?id=zqlj_sign&sign=abc123ef",
        )

        assertEquals(YamiboDailyCheckInState.CHECKED_IN, page.status.state)
        assertNull(page.signToken)
    }

    @Test
    fun parsesAlreadyCheckedInSubmissionMessage() {
        val page = parseDailyCheckInSubmission(
            """<script>showDialog('您今天已经打过卡了，请勿重复操作！');</script>""",
            "$YAMIBO_ORIGIN/plugin.php?id=zqlj_sign&sign=abc123ef",
        )

        assertEquals(YamiboDailyCheckInState.CHECKED_IN, page.status.state)
        assertNull(page.signToken)
    }

    @Test
    fun doesNotTreatRankingStatusAsViewerCheckIn() {
        assertCheckInError(YamiboDailyCheckInErrorCode.INVALID_RESPONSE) {
            parseDailyCheckInPage(
                """<th>今日状态</th><td>今日已打卡</td>""",
                "$YAMIBO_ORIGIN/plugin.php?id=zqlj_sign",
            )
        }
    }

    @Test
    fun rejectsLoginRedirect() {
        assertCheckInError(YamiboDailyCheckInErrorCode.NOT_AUTHENTICATED) {
            parseDailyCheckInPage(
                """<form id="loginform"></form>""",
                "$YAMIBO_ORIGIN/member.php?mod=logging&action=login",
            )
        }
    }

    @Test
    fun surfacesPluginErrorMessage() {
        try {
            parseDailyCheckInSubmission(
                """<div id="messagetext"><p>当前用户组无权打卡&amp;领取奖励</p></div>""",
                "$YAMIBO_ORIGIN/plugin.php?id=zqlj_sign&sign=abc123ef",
            )
            fail("Expected YamiboDailyCheckInException")
        } catch (error: YamiboDailyCheckInException) {
            assertEquals(YamiboDailyCheckInErrorCode.SERVER_ERROR, error.code)
            assertEquals("当前用户组无权打卡&领取奖励", error.message)
        }
    }

    private fun assertCheckInError(
        expected: YamiboDailyCheckInErrorCode,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected YamiboDailyCheckInException")
        } catch (error: YamiboDailyCheckInException) {
            assertEquals(expected, error.code)
        }
    }
}
