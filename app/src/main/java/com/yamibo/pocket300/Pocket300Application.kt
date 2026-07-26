package com.yamibo.pocket300

import android.app.Application
import com.yamibo.pocket300.api.YamiboApi
import com.yamibo.pocket300.data.download.ThreadDownloadRepository
import com.yamibo.pocket300.logging.AppLogger

class Pocket300Application : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(BuildConfig.DEBUG)
        ThreadDownloadRepository.getInstance(this)
        AppLogger.info(TAG) { "Application process started" }
    }

    companion object {
        val api: YamiboApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { YamiboApi() }

        const val TAG = "Application"
    }
}
