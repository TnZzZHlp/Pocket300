package com.yamibo.pocket300.api

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yamibo.pocket300.logging.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

fun interface YamiboRequestGate {
    suspend fun awaitReady()
}

class YamiboWafException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs the Yamibo browser verification in a real WebView and shares its cookies with OkHttp.
 *
 * The WebView must be attached to a visible window, even though the host can make it effectively
 * invisible. A single process-level instance is used so every API request waits for the same
 * verification result.
 */
class YamiboWafGate(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : YamiboRequestGate {
    private val ready = CompletableDeferred<Unit>()
    private var attachedWebView: WebView? = null
    private var loadingWebView: WebView? = null
    private var checkRunnable: Runnable? = null
    private var startedAtMillis = 0L
    private var timeoutLogged = false

    override suspend fun awaitReady() {
        withTimeoutOrNull(timeoutMillis) { ready.await() }
            ?: throw YamiboWafException("百合会浏览器验证超时，请稍后重试")
    }

    /** Attaches and starts the verification WebView. Must be called on the main thread. */
    fun attach(webView: WebView) {
        checkMainThread()
        if (ready.isCompleted) return
        if (attachedWebView === webView) return

        attachedWebView?.stopLoading()
        cancelCookieCheck()
        attachedWebView = webView
        loadingWebView = webView
        startedAtMillis = SystemClock.uptimeMillis()
        timeoutLogged = false

        configureWebView(webView)
        webView.loadUrl("$YAMIBO_ORIGIN/")
        scheduleCookieCheck(webView)
        AppLogger.debug(TAG) { "Started Yamibo browser verification" }
    }

    /** Detaches a host WebView without clearing cookies or cancelling a completed gate. */
    fun detach(webView: WebView) {
        checkMainThread()
        if (attachedWebView !== webView) return
        cancelCookieCheck()
        webView.stopLoading()
        attachedWebView = null
        loadingWebView = null
    }

    private fun configureWebView(webView: WebView) {
        cookieManager.setAcceptCookie(true)
        @Suppress("DEPRECATION")
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (view === attachedWebView) checkCookie(view)
            }
        }
    }

    private fun scheduleCookieCheck(webView: WebView) {
        cancelCookieCheck()
        val runnable = Runnable { checkCookie(webView) }
        checkRunnable = runnable
        mainHandler.postDelayed(runnable, COOKIE_POLL_INTERVAL_MILLIS)
    }

    private fun checkCookie(webView: WebView) {
        if (ready.isCompleted || webView !== attachedWebView || webView !== loadingWebView) return
        if (hasYamiboWafCookie(cookieManager.getCookie(YAMIBO_ORIGIN))) {
            cookieManager.flush()
            ready.complete(Unit)
            cancelCookieCheck()
            AppLogger.info(TAG) { "Yamibo browser verification completed" }
            return
        }

        if (SystemClock.uptimeMillis() - startedAtMillis >= timeoutMillis) {
            if (!timeoutLogged) {
                timeoutLogged = true
                AppLogger.warn(TAG) { "Yamibo browser verification did not produce a WAF cookie" }
            }
            cancelCookieCheck()
            return
        }
        scheduleCookieCheck(webView)
    }

    private fun cancelCookieCheck() {
        checkRunnable?.let(mainHandler::removeCallbacks)
        checkRunnable = null
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "YamiboWafGate must be accessed on the main thread"
        }
    }

    private companion object {
        const val TAG = "YamiboWafGate"
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        const val COOKIE_POLL_INTERVAL_MILLIS = 200L
    }
}

internal const val YAMIBO_WAF_COOKIE_NAME = "nox_jst_v1"

internal fun hasYamiboWafCookie(cookieHeader: String?): Boolean = cookieHeader
    .orEmpty()
    .split(';')
    .any { item ->
        val separator = item.indexOf('=')
        separator > 0 &&
            item.substring(0, separator).trim() == YAMIBO_WAF_COOKIE_NAME &&
            item.substring(separator + 1).trim().isNotEmpty()
    }
