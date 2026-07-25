package com.yamibo.pocket300

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yamibo.pocket300.logging.AppLogger
import com.yamibo.pocket300.ui.Pocket300App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.debug(TAG) { "Main activity created" }
        enableEdgeToEdge()
        setContent { Pocket300App() }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
