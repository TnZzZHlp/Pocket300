package com.yamibo.pocket300.ui.screens

import com.yamibo.pocket300.logging.AppLogEntry
import com.yamibo.pocket300.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LogScreenTest {
    @Test
    fun formatsLogEntryForSharing() {
        val timestamp = Instant.parse("2026-07-26T12:34:56.789Z").toEpochMilli()
        val expectedTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val entry = AppLogEntry(
            id = 1,
            timestampMillis = timestamp,
            level = LogLevel.WARN,
            component = "Transport",
            message = "request failed",
            stackTrace = "example stack trace",
        )

        val formatted = formatLogEntry(entry)

        assertTrue(
            formatted.startsWith(
                "%02d-%02d %02d:%02d:%02d.%03d WARN [Transport] request failed".format(
                    expectedTime.monthValue,
                    expectedTime.dayOfMonth,
                    expectedTime.hour,
                    expectedTime.minute,
                    expectedTime.second,
                    expectedTime.nano / 1_000_000,
                ),
            ),
        )
        assertTrue(formatted.endsWith("\nexample stack trace"))
    }

    @Test
    fun omitsStackTraceLineWhenThereIsNoThrowable() {
        val entry = AppLogEntry(
            id = 1,
            timestampMillis = 0,
            level = LogLevel.INFO,
            component = "Application",
            message = "started",
            stackTrace = null,
        )

        assertEquals(1, formatLogEntry(entry).lines().size)
    }

    @Test
    fun filtersLogEntriesByLevelAndQueryInReverseChronologicalOrder() {
        val entries = listOf(
            AppLogEntry(1, 100L, LogLevel.INFO, "Network", "Request succeeded", null),
            AppLogEntry(2, 200L, LogLevel.ERROR, "Reader", "Decode failed", "java.io.IOException: EOF"),
            AppLogEntry(3, 300L, LogLevel.WARN, "Reader", "Slow response", null),
            AppLogEntry(4, 400L, LogLevel.ERROR, "Auth", "Token expired", null),
        )

        assertEquals(listOf(4L, 2L), filterLogEntries(entries, LogLevel.ERROR, "").map(AppLogEntry::id))
        assertEquals(listOf(3L, 2L), filterLogEntries(entries, null, "reader").map(AppLogEntry::id))
        assertEquals(listOf(2L), filterLogEntries(entries, LogLevel.ERROR, "ioexception").map(AppLogEntry::id))
    }
}
