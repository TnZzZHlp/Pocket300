package com.yamibo.pocket300.logging

import android.util.Log

internal enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

internal fun interface LogSink {
    fun write(
        level: LogLevel,
        tag: String,
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

/**
 * Process-wide logging entry point.
 *
 * It starts with a no-op sink so local JVM tests never call Android framework
 * stubs. [initialize] installs the Logcat sink during Application startup.
 */
internal object AppLogger {
    @Volatile
    private var logger = Logger(LogLevel.ERROR, LogSink { _, _, _, _ -> })

    fun initialize(isDebugBuild: Boolean) {
        logger = Logger(
            minimumLevel = if (isDebugBuild) LogLevel.VERBOSE else LogLevel.INFO,
            sink = AndroidLogSink,
        )
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
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val logcatTag = "Pocket300/$tag"
        when (level) {
            LogLevel.VERBOSE -> Log.v(logcatTag, message, throwable)
            LogLevel.DEBUG -> Log.d(logcatTag, message, throwable)
            LogLevel.INFO -> Log.i(logcatTag, message, throwable)
            LogLevel.WARN -> Log.w(logcatTag, message, throwable)
            LogLevel.ERROR -> Log.e(logcatTag, message, throwable)
        }
    }
}
