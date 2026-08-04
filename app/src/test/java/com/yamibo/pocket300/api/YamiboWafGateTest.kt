package com.yamibo.pocket300.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
