package com.yamibo.pocket300

import android.app.Application
import com.yamibo.pocket300.logging.AppLogger

class Pocket300Application : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(BuildConfig.DEBUG)
        AppLogger.info(TAG) { "Application process started" }
    }

    private companion object {
        const val TAG = "Application"
    }
}
