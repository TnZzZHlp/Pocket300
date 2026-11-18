package com.yamibo.pocket300

import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yamibo.pocket300.logging.AppLogger
import com.yamibo.pocket300.ui.Pocket300App
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var wafWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.debug(TAG) { "Main activity created" }
        enableEdgeToEdge()

        val nightMode =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val surfaceColor = if (nightMode) 0xFF211F1B.toInt() else 0xFFFAF8F3.toInt()
        val dividerColor = if (nightMode) 0xFF484036.toInt() else 0xFFE4DED4.toInt()
        val titleColor = if (nightMode) 0xFFE8E1D5.toInt() else 0xFF302E29.toInt()
        val subtitleColor = if (nightMode) 0xFFB8AFA2.toInt() else 0xFF6B655B.toInt()
        val actionColor = if (nightMode) 0xFFD9B58B.toInt() else 0xFF755338.toInt()

        val root = FrameLayout(this)
        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        val verificationHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(surfaceColor)
            val horizontalPad = dp(16)
            val verticalPad = dp(10)
            setPadding(horizontalPad, verticalPad, dp(8), verticalPad)
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(R.string.waf_verification_title)
                            setTextColor(titleColor)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        },
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = getString(R.string.waf_verification_subtitle)
                            setTextColor(subtitleColor)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        },
                    )
                },
            )
            addView(
                Button(this@MainActivity, null, android.R.attr.borderlessButtonStyle).apply {
                    text = getString(R.string.waf_verification_reload)
                    setTextColor(actionColor)
                    setOnClickListener { Pocket300Application.wafGate.reloadVerification() }
                },
            )
            addView(
                Button(this@MainActivity, null, android.R.attr.borderlessButtonStyle).apply {
                    text = getString(R.string.waf_verification_cancel)
                    setTextColor(actionColor)
                    setOnClickListener { Pocket300Application.wafGate.cancelVerification() }
                },
            )
        }
        val divider = View(this).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1).coerceAtLeast(1),
            )
        }
        val verificationContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            alpha = 0f
            translationZ = -1f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            addView(verificationHeader)
            addView(divider)
            addView(webView)
        }
        ViewCompat.setOnApplyWindowInsetsListener(verificationContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        val composeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setContent { Pocket300App() }
        }
        root.addView(verificationContainer)
        root.addView(composeView)
        setContentView(root)

        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                Pocket300Application.wafGate.cancelVerification()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Pocket300Application.wafGate.interactiveVerificationVisible.collect { visible ->
                    if (visible) {
                        verificationContainer.alpha = 1f
                        verificationContainer.translationZ = 16f
                        verificationContainer.importantForAccessibility =
                            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        verificationContainer.bringToFront()
                        backCallback.isEnabled = true
                    } else {
                        verificationContainer.alpha = 0f
                        verificationContainer.translationZ = -1f
                        verificationContainer.importantForAccessibility =
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        composeView.bringToFront()
                        backCallback.isEnabled = false
                    }
                }
            }
        }

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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "MainActivity"
    }
}
