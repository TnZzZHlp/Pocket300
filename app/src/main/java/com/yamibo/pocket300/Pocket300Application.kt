package com.yamibo.pocket300

import android.app.Application
import com.yamibo.pocket300.api.YamiboApi
import com.yamibo.pocket300.api.YamiboClient
import com.yamibo.pocket300.api.YamiboWafGate
import com.yamibo.pocket300.data.download.ThreadDownloadManager
import com.yamibo.pocket300.logging.AppLogger

class Pocket300Application : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(BuildConfig.DEBUG)
        ThreadDownloadManager.getInstance(this)
        AppLogger.info(TAG) { "Application process started" }
    }

    companion object {
        val wafGate: YamiboWafGate by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            YamiboWafGate()
        }
        val api: YamiboApi by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            YamiboApi(YamiboClient(requestGate = wafGate))
        }

        const val TAG = "Application"
    }
}
