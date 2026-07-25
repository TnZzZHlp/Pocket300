package com.yamibo.pocket300.api

import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YamiboClientLoggingTest {
    @Test
    fun requestSummaryExcludesSensitiveRequestData() {
        val request = Request.Builder()
            .url("$YAMIBO_ORIGIN/api/mobile/index.php?username=alice&token=query-secret")
            .header("Cookie", "auth=cookie-secret")
            .post(
                FormBody.Builder()
                    .add("password", "form-secret")
                    .build(),
            )
            .build()

        val summary = request.safeLogSummary()

        assertEquals("POST /api/mobile/index.php", summary)
        assertFalse(summary.contains("alice"))
        assertFalse(summary.contains("secret"))
    }
}
