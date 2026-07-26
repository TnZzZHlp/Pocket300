package com.yamibo.pocket300.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerTest {
    @Test
    fun usesStablePropertySafeLogcatTag() {
        assertEquals("Pocket300", LOGCAT_TAG)
        assertTrue(LOGCAT_TAG.matches(Regex("""[A-Za-z0-9_.-]+""")))
    }

    @Test
    fun suppressesMessagesBelowMinimumLevelWithoutEvaluatingThem() {
        val entries = mutableListOf<LogEntry>()
        var evaluated = false
        val logger = testLogger(LogLevel.INFO, entries)

        logger.debug("Transport") {
            evaluated = true
            "request started"
        }

        assertFalse(evaluated)
        assertEquals(emptyList<LogEntry>(), entries)
    }

    @Test
    fun writesMessagesAtOrAboveMinimumLevel() {
        val entries = mutableListOf<LogEntry>()
        val logger = testLogger(LogLevel.INFO, entries)

        logger.info("Application") { "started" }
        logger.warn("Transport") { "retrying" }

        assertEquals(
            listOf(
                LogEntry(LogLevel.INFO, "Application", "started", null),
                LogEntry(LogLevel.WARN, "Transport", "retrying", null),
            ),
            entries,
        )
    }

    @Test
    fun forwardsThrowableToSink() {
        val entries = mutableListOf<LogEntry>()
        val failure = IllegalStateException("broken")
        val logger = testLogger(LogLevel.DEBUG, entries)

        logger.error("Database", failure) { "write failed" }

        assertSame(failure, entries.single().throwable)
    }

    private fun testLogger(minimumLevel: LogLevel, entries: MutableList<LogEntry>) = Logger(
        minimumLevel,
        LogSink { level, tag, message, throwable ->
            entries += LogEntry(level, tag, message, throwable)
        },
    )

    private data class LogEntry(
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )
}
