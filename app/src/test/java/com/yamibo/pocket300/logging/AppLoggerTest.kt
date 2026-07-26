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

    @Test
    fun inMemorySinkRetainsOnlyNewestEntries() {
        var timestamp = 100L
        val sink = InMemoryLogSink(capacity = 2) { timestamp++ }

        sink.write(LogLevel.INFO, "First", "one", null)
        sink.write(LogLevel.WARN, "Second", "two", null)
        sink.write(LogLevel.ERROR, "Third", "three", null)

        assertEquals(
            listOf(
                AppLogEntry(1, 101, LogLevel.WARN, "Second", "two", null),
                AppLogEntry(2, 102, LogLevel.ERROR, "Third", "three", null),
            ),
            sink.entries.value,
        )
    }

    @Test
    fun inMemorySinkIncludesThrowableStackTraceAndCanBeCleared() {
        val sink = InMemoryLogSink(capacity = 2) { 123L }
        val failure = IllegalStateException("broken")

        sink.write(LogLevel.ERROR, "Database", "write failed", failure)

        val entry = sink.entries.value.single()
        assertTrue(entry.stackTrace.orEmpty().contains("IllegalStateException: broken"))

        sink.clear()

        assertEquals(emptyList<AppLogEntry>(), sink.entries.value)
    }

    @Test
    fun inMemorySinkIsSafeForConcurrentWriters() {
        val sink = InMemoryLogSink(capacity = 50) { 123L }
        val writers = List(4) { writer ->
            Thread {
                repeat(100) { message ->
                    sink.write(LogLevel.INFO, "Writer$writer", "$message", null)
                }
            }
        }

        writers.forEach(Thread::start)
        writers.forEach(Thread::join)

        val entries = sink.entries.value
        assertEquals(50, entries.size)
        assertEquals(50, entries.map(AppLogEntry::id).distinct().size)
        assertEquals(entries.map(AppLogEntry::id).sorted(), entries.map(AppLogEntry::id))
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
