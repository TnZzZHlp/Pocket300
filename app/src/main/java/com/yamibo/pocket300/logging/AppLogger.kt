package com.yamibo.pocket300.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

internal data class AppLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val level: LogLevel,
    val component: String,
    val message: String,
    val stackTrace: String?,
)

internal fun interface LogSink {
    fun write(
        level: LogLevel,
        component: String,
        message: String,
        throwable: Throwable?,
    )
}

internal class Logger(
    private val minimumLevel: LogLevel,
    private val sink: LogSink,
) {
    fun verbose(tag: String, throwable: Throwable? = null, message: () -> String) {
        log(LogLevel.VERBOSE, tag, throwable, message)
    }

    fun debug(tag: String, throwable: Throwable? = null, message: () -> String) {
        log(LogLevel.DEBUG, tag, throwable, message)
    }

    fun info(tag: String, throwable: Throwable? = null, message: () -> String) {
        log(LogLevel.INFO, tag, throwable, message)
    }

    fun warn(tag: String, throwable: Throwable? = null, message: () -> String) {
        log(LogLevel.WARN, tag, throwable, message)
    }

    fun error(tag: String, throwable: Throwable? = null, message: () -> String) {
        log(LogLevel.ERROR, tag, throwable, message)
    }

    private fun log(
        level: LogLevel,
        tag: String,
        throwable: Throwable?,
        message: () -> String,
    ) {
        if (level < minimumLevel) return
        sink.write(level, tag, message(), throwable)
    }
}

internal class InMemoryLogSink(
    private val capacity: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) : LogSink {
    private val lock = Any()
    private var nextId = 0L
    private val mutableEntries = MutableStateFlow<List<AppLogEntry>>(emptyList())

    init {
        require(capacity > 0) { "Log capacity must be greater than zero" }
    }

    val entries: StateFlow<List<AppLogEntry>> = mutableEntries.asStateFlow()

    override fun write(
        level: LogLevel,
        component: String,
        message: String,
        throwable: Throwable?,
    ) {
        synchronized(lock) {
            val entry = AppLogEntry(
                id = nextId++,
                timestampMillis = clock(),
                level = level,
                component = component,
                message = message,
                stackTrace = throwable?.stackTraceToString(),
            )
            val current = mutableEntries.value
            mutableEntries.value = if (current.size < capacity) {
                current + entry
            } else {
                current.drop(1) + entry
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            mutableEntries.value = emptyList()
        }
    }
}

private class CompositeLogSink(
    private vararg val sinks: LogSink,
) : LogSink {
    override fun write(
        level: LogLevel,
        component: String,
        message: String,
        throwable: Throwable?,
    ) {
        sinks.forEach { it.write(level, component, message, throwable) }
    }
}

/**
 * Process-wide logging entry point.
 *
 * It starts with a no-op sink so local JVM tests never call Android framework
 * stubs. [initialize] installs the Logcat sink during Application startup.
 */
internal object AppLogger {
    private val inMemorySink = InMemoryLogSink(APP_LOG_CAPACITY)

    @Volatile
    private var logger = Logger(LogLevel.ERROR, LogSink { _, _, _, _ -> })

    val entries: StateFlow<List<AppLogEntry>>
        get() = inMemorySink.entries

    fun initialize(isDebugBuild: Boolean) {
        logger = Logger(
            minimumLevel = if (isDebugBuild) LogLevel.VERBOSE else LogLevel.INFO,
            sink = CompositeLogSink(inMemorySink, AndroidLogSink),
        )
    }

    fun clear() {
        inMemorySink.clear()
    }

    fun verbose(tag: String, throwable: Throwable? = null, message: () -> String) {
        logger.verbose(tag, throwable, message)
    }

    fun debug(tag: String, throwable: Throwable? = null, message: () -> String) {
        logger.debug(tag, throwable, message)
    }

    fun info(tag: String, throwable: Throwable? = null, message: () -> String) {
        logger.info(tag, throwable, message)
    }

    fun warn(tag: String, throwable: Throwable? = null, message: () -> String) {
        logger.warn(tag, throwable, message)
    }

    fun error(tag: String, throwable: Throwable? = null, message: () -> String) {
        logger.error(tag, throwable, message)
    }
}

private object AndroidLogSink : LogSink {
    override fun write(
        level: LogLevel,
        component: String,
        message: String,
        throwable: Throwable?,
    ) {
        val logcatMessage = "[$component] $message"
        when (level) {
            LogLevel.VERBOSE -> Log.v(LOGCAT_TAG, logcatMessage, throwable)
            LogLevel.DEBUG -> Log.d(LOGCAT_TAG, logcatMessage, throwable)
            LogLevel.INFO -> Log.i(LOGCAT_TAG, logcatMessage, throwable)
            LogLevel.WARN -> Log.w(LOGCAT_TAG, logcatMessage, throwable)
            LogLevel.ERROR -> Log.e(LOGCAT_TAG, logcatMessage, throwable)
        }
    }
}

/**
 * A single property-safe tag keeps filtering predictable and lets devices with
 * restrictive defaults enable logs via `adb shell setprop log.tag.Pocket300 V`.
 */
internal const val LOGCAT_TAG = "Pocket300"
internal const val APP_LOG_CAPACITY = 500
