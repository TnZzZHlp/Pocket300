package com.yamibo.pocket300

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import com.yamibo.pocket300.logging.AppLogger
import com.yamibo.pocket300.ui.Pocket300App

class MainActivity : ComponentActivity() {
    private var wafWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.debug(TAG) { "Main activity created" }
        enableEdgeToEdge()

        val root = FrameLayout(this)
        val webView = WebView(this).apply {
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val composeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setContent { Pocket300App() }
        }
        root.addView(webView)
        root.addView(composeView)
        setContentView(root)

        wafWebView = webView
        Pocket300Application.wafGate.attach(webView)
    }

    override fun onPause() {
        wafWebView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        wafWebView?.onResume()
    }

    override fun onDestroy() {
        wafWebView?.let { webView ->
            Pocket300Application.wafGate.detach(webView)
            webView.destroy()
        }
        wafWebView = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
