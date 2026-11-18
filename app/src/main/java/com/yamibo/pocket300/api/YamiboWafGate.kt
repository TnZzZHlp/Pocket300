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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface YamiboRequestGate {
    suspend fun awaitReady()
}

class YamiboWafException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs Yamibo browser verification on demand in a real [WebView] and shares cookies with OkHttp.
 *
 * Verification starts only when an HTTP response indicates a browser challenge. If verification
 * completes quickly in the background, no UI is shown; otherwise [interactiveVerificationVisible]
 * emits `true` so the host Activity can bring the WebView to the foreground for user interaction.
 */
class YamiboWafGate(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : YamiboRequestGate {
    private val lock = Any()
    private val _interactiveVerificationVisible = MutableStateFlow(false)
    val interactiveVerificationVisible: StateFlow<Boolean> =
        _interactiveVerificationVisible.asStateFlow()

    private var attachedWebView: WebView? = null
    private var activeSession: VerificationSession? = null
    private var lastVerifiedAtMillis = 0L

    private var pollRunnable: Runnable? = null
    private var uiRevealRunnable: Runnable? = null
    private var timeoutRunnable: Runnable? = null
    private var attachTimeoutRunnable: Runnable? = null

    override suspend fun awaitReady() {
        val session = synchronized(lock) {
            val now = SystemClock.uptimeMillis()
            if (lastVerifiedAtMillis > 0L && now - lastVerifiedAtMillis < RECENT_VERIFICATION_REUSE_MILLIS) {
                return
            }
            activeSession ?: VerificationSession().also { created ->
                activeSession = created
                mainHandler.post { startSessionOnMainThread(created) }
            }
        }
        session.deferred.await()
    }

    /** Attaches the host verification WebView without eagerly loading until challenged. */
    fun attach(webView: WebView) {
        checkMainThread()
        if (attachedWebView === webView) return

        attachedWebView?.stopLoading()
        attachedWebView = webView
        configureWebView(webView)

        val waitingSession = synchronized(lock) { activeSession }
        if (waitingSession != null && !waitingSession.deferred.isCompleted && !waitingSession.startedLoading) {
            startSessionLoading(webView, waitingSession)
        }
    }

    /** Detaches a host WebView without clearing cookies. */
    fun detach(webView: WebView) {
        checkMainThread()
        if (attachedWebView !== webView) return

        cancelMainThreadCallbacks()
        webView.stopLoading()
        attachedWebView = null
        _interactiveVerificationVisible.value = false

        val pending = synchronized(lock) {
            activeSession?.also { activeSession = null }
        }
        pending?.deferred?.completeExceptionally(
            YamiboWafException("需要打开应用完成百合会浏览器验证"),
        )
    }

    /** Cancels any active verification session and hides the verification overlay. */
    fun cancelVerification() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::cancelVerification)
            return
        }
        val session = synchronized(lock) {
            activeSession?.also { activeSession = null }
        } ?: run {
            _interactiveVerificationVisible.value = false
            return
        }
        cleanupSessionOnMainThread()
        session.deferred.completeExceptionally(YamiboWafException("已取消百合会浏览器验证"))
        AppLogger.info(TAG) { "Yamibo browser verification cancelled by user" }
    }

    /** Reloads the verification page in the attached WebView while keeping the session active. */
    fun reloadVerification() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::reloadVerification)
            return
        }
        val webView = attachedWebView ?: return
        val session = synchronized(lock) { activeSession } ?: return
        if (session.deferred.isCompleted) return
        session.pageCommitted = false
        session.evalInFlight = false
        webView.loadUrl("$YAMIBO_ORIGIN/")
        AppLogger.debug(TAG) { "Reloaded Yamibo browser verification page" }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = POCKET300_USER_AGENT
        webView.setBackgroundColor(Color.WHITE)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView, url: String?) {
                handlePageUpdate(view, url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                handlePageUpdate(view, url)
            }
        }
    }

    private fun handlePageUpdate(view: WebView, url: String?) {
        if (view !== attachedWebView || !url.orEmpty().startsWith(YAMIBO_ORIGIN)) return
        val session = synchronized(lock) { activeSession } ?: return
        if (session.deferred.isCompleted) return
        session.pageCommitted = true
        checkVerificationState(view, session)
    }

    private fun startSessionOnMainThread(session: VerificationSession) {
        val isStillActive = synchronized(lock) {
            activeSession === session && !session.deferred.isCompleted
        }
        if (!isStillActive) return
        val webView = attachedWebView
        if (webView == null) {
            scheduleAttachTimeout(session)
            return
        }
        if (!session.startedLoading) {
            startSessionLoading(webView, session)
        }
    }

    private fun startSessionLoading(webView: WebView, session: VerificationSession) {
        cancelMainThreadCallbacks()
        session.startedLoading = true
        session.initialWafCookie = extractCookieValue(
            cookieManager.getCookie(YAMIBO_ORIGIN),
            YAMIBO_WAF_COOKIE_NAME,
        )
        session.pageCommitted = false
        session.evalInFlight = false

        webView.loadUrl("$YAMIBO_ORIGIN/")
        AppLogger.debug(TAG) { "Started on-demand Yamibo browser verification" }

        val reveal = Runnable {
            val stillActive = synchronized(lock) {
                activeSession === session && !session.deferred.isCompleted
            }
            if (stillActive) {
                _interactiveVerificationVisible.value = true
                AppLogger.debug(TAG) { "Revealed interactive Yamibo browser verification view" }
            }
        }
        uiRevealRunnable = reveal
        mainHandler.postDelayed(reveal, UI_REVEAL_DELAY_MILLIS)

        val timeout = Runnable {
            failSession(session, "百合会浏览器验证超时，请稍后重试")
        }
        timeoutRunnable = timeout
        mainHandler.postDelayed(timeout, timeoutMillis)

        scheduleVerificationPoll(webView, session)
    }

    private fun scheduleAttachTimeout(session: VerificationSession) {
        attachTimeoutRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            val stillWaitingWithoutView = synchronized(lock) {
                activeSession === session && !session.deferred.isCompleted && attachedWebView == null
            }
            if (stillWaitingWithoutView) {
                failSession(session, "需要打开应用完成百合会浏览器验证")
            }
        }
        attachTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, ATTACH_TIMEOUT_MILLIS)
    }

    private fun scheduleVerificationPoll(webView: WebView, session: VerificationSession) {
        pollRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable { checkVerificationState(webView, session) }
        pollRunnable = runnable
        mainHandler.postDelayed(runnable, COOKIE_POLL_INTERVAL_MILLIS)
    }

    private fun checkVerificationState(webView: WebView, session: VerificationSession) {
        val isActive = synchronized(lock) {
            activeSession === session && !session.deferred.isCompleted && attachedWebView === webView
        }
        if (!isActive) return

        if (hasFreshYamiboWafCookie(session.initialWafCookie, cookieManager.getCookie(YAMIBO_ORIGIN))) {
            completeSession(session)
            return
        }

        if (session.pageCommitted && !session.evalInFlight) {
            session.evalInFlight = true
            webView.evaluateJavascript(DETECT_DISCUZ_READY_SCRIPT) { rawResult ->
                session.evalInFlight = false
                val stillActive = synchronized(lock) {
                    activeSession === session && !session.deferred.isCompleted && attachedWebView === webView
                }
                if (stillActive && parseWebViewVerificationEvalResult(rawResult)) {
                    completeSession(session)
                }
            }
        }

        scheduleVerificationPoll(webView, session)
    }

    private fun completeSession(session: VerificationSession) {
        val won = synchronized(lock) {
            if (activeSession !== session || session.deferred.isCompleted) return
            activeSession = null
            lastVerifiedAtMillis = SystemClock.uptimeMillis()
            true
        }
        if (!won) return
        cookieManager.flush()
        cleanupSessionOnMainThread()
        session.deferred.complete(Unit)
        AppLogger.info(TAG) { "Yamibo browser verification completed" }
    }

    private fun failSession(session: VerificationSession, message: String) {
        val won = synchronized(lock) {
            if (activeSession !== session || session.deferred.isCompleted) return
            activeSession = null
            true
        }
        if (!won) return
        cleanupSessionOnMainThread()
        session.deferred.completeExceptionally(YamiboWafException(message))
        AppLogger.warn(TAG) { "Yamibo browser verification failed: $message" }
    }

    private fun cleanupSessionOnMainThread() {
        cancelMainThreadCallbacks()
        attachedWebView?.stopLoading()
        _interactiveVerificationVisible.value = false
    }

    private fun cancelMainThreadCallbacks() {
        pollRunnable?.let(mainHandler::removeCallbacks)
        pollRunnable = null
        uiRevealRunnable?.let(mainHandler::removeCallbacks)
        uiRevealRunnable = null
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        attachTimeoutRunnable?.let(mainHandler::removeCallbacks)
        attachTimeoutRunnable = null
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "YamiboWafGate must be accessed on the main thread"
        }
    }

    private class VerificationSession(
        val deferred: CompletableDeferred<Unit> = CompletableDeferred(),
        @Volatile var initialWafCookie: String? = null,
        @Volatile var pageCommitted: Boolean = false,
        @Volatile var evalInFlight: Boolean = false,
        @Volatile var startedLoading: Boolean = false,
    )

    private companion object {
        const val TAG = "YamiboWafGate"
        const val DEFAULT_TIMEOUT_MILLIS = 45_000L
        const val ATTACH_TIMEOUT_MILLIS = 5_000L
        const val UI_REVEAL_DELAY_MILLIS = 1_500L
        const val COOKIE_POLL_INTERVAL_MILLIS = 350L
        const val RECENT_VERIFICATION_REUSE_MILLIS = 3_000L
        const val DETECT_DISCUZ_READY_SCRIPT = """
            (function() {
                try {
                    if (document.readyState === 'loading') return 'loading';
                    var text = document.documentElement ? document.documentElement.innerHTML : '';
                    if ((text.indexOf('Powered by Discuz!') !== -1 || text.indexOf('discuz_uid') !== -1) &&
                        text.indexOf('nox_jst') === -1) {
                        return 'discuz_ready';
                    }
                    return text.indexOf('nox_jst') !== -1 ? 'challenge' : 'pending';
                } catch (e) {
                    return 'error';
                }
            })()
        """
    }
}

internal const val YAMIBO_WAF_COOKIE_NAME = "nox_jst_v1"

private val CHALLENGE_STATUS_CODES = setOf(403, 412, 429, 503)
private val HTML_START_PREFIXES = listOf("<!DOCTYPE", "<html", "<head", "<body", "<script")
private val DISCUZ_MARKERS = listOf(
    "Powered by Discuz!",
    "discuz_uid",
    "id=\"messagetext\"",
    "name=\"formhash\"",
)
private val CHALLENGE_MARKERS = listOf(
    "nox_jst",
    "bdwaf",
    "cf-browser-verification",
    "_cf_chl_opt",
    "Checking your browser",
    "Just a moment",
    "浏览器验证",
    "正在验证",
    "安全验证",
    "人机验证",
    "访问验证",
)

internal fun extractCookieValue(cookieHeader: String?, cookieName: String): String? = cookieHeader
    .orEmpty()
    .split(';')
    .firstNotNullOfOrNull { item ->
        val separator = item.indexOf('=')
        if (separator <= 0) return@firstNotNullOfOrNull null
        if (item.substring(0, separator).trim() != cookieName) return@firstNotNullOfOrNull null
        item.substring(separator + 1).trim().takeIf(String::isNotEmpty)
    }

internal fun hasYamiboWafCookie(cookieHeader: String?): Boolean =
    extractCookieValue(cookieHeader, YAMIBO_WAF_COOKIE_NAME) != null

internal fun hasFreshYamiboWafCookie(
    initialCookieValue: String?,
    currentCookieHeader: String?,
): Boolean {
    val currentValue = extractCookieValue(currentCookieHeader, YAMIBO_WAF_COOKIE_NAME)
        ?: return false
    return currentValue != initialCookieValue
}

internal fun parseWebViewVerificationEvalResult(rawResult: String?): Boolean =
    rawResult?.trim()?.removeSurrounding("\"") == "discuz_ready"

internal fun isYamiboBrowserChallenge(
    statusCode: Int,
    contentType: String?,
    body: String,
    expectJson: Boolean,
): Boolean {
    if (containsDiscuzMarker(body)) return false
    val isHtmlContentType = contentType?.contains("text/html", ignoreCase = true) == true
    val htmlOrChallenge = isHtmlContentType || looksLikeHtml(body) || containsChallengeMarker(body)

    if (statusCode in CHALLENGE_STATUS_CODES) {
        return htmlOrChallenge
    }
    if (statusCode in 200..299) {
        return if (expectJson) {
            !body.trimStart().startsWith("{") && htmlOrChallenge
        } else {
            containsChallengeMarker(body)
        }
    }
    return false
}

private fun looksLikeHtml(body: String): Boolean {
    val trimmed = body.trimStart()
    if (trimmed.startsWith("<?xml", ignoreCase = true)) return false
    return HTML_START_PREFIXES.any { prefix -> trimmed.startsWith(prefix, ignoreCase = true) }
}

private fun containsDiscuzMarker(body: String): Boolean =
    DISCUZ_MARKERS.any { marker -> body.contains(marker, ignoreCase = true) }

private fun containsChallengeMarker(body: String): Boolean =
    CHALLENGE_MARKERS.any { marker -> body.contains(marker, ignoreCase = true) }
