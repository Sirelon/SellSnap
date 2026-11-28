package com.sirelon.sellsnap.features.seller.auth.presentation

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.data.OlxConfig
import com.sirelon.sellsnap.features.seller.auth.data.OlxLoginModeRepository
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import org.koin.compose.koinInject

/**
 * Picks the OLX login screen from the Firestore switch `config/olx_login.systemBrowser`
 * (OlxLoginModeRepository): a Custom Tab when on, otherwise the in-app WebView below. Until the
 * switch is read, the in-app WebView is used.
 */
@Composable
actual fun rememberOlxAuthLauncher(
    forceReauth: Boolean,
    onDismissed: (OlxAuthDismissReason) -> Unit,
): (String) -> Unit {
    val loginMode = koinInject<OlxLoginModeRepository>()
    val systemBrowser by produceState(initialValue = false, loginMode) { value = loginMode.useSystemBrowser() }
    val inApp = rememberInAppOlxAuthLauncher(forceReauth, onDismissed)
    val browser = rememberSystemBrowserOlxAuthLauncher(
        forceReauth = forceReauth,
        onDismissed = onDismissed,
        onNoBrowser = inApp,
    )
    return remember(systemBrowser, inApp, browser) { if (systemBrowser) browser else inApp }
}

/**
 * The OLX login in an in-app WebView instead of a Custom Tab.
 *
 * Why (SIR-195, `.claude/rules/olx-api.md`): a phone is sent to `m.olx.<tld>`, logs in there, and
 * OLX's page jumps straight back to `www.olx.<tld>/oauth/authorize`. Arriving within about a
 * second of the login, `/oauth/authorize` rejects the brand-new session, checks `login.olx.<tld>`
 * silently, logs the seller out and shows the OLX home page. Five seconds later the same request
 * succeeds and redirects to our `redirect_uri` with a code (Inferred: from tests on olx.ro,
 * 2026-11-22; the cause on OLX's side is unknown). A Custom Tab cannot hold that jump, so the app
 * owns the WebView. It sends a desktop user agent, which keeps the whole login on `www` and avoids
 * the `m.olx` jump ([desktopUserAgent], SIR-203). It still delays any jump to `/oauth/authorize`
 * that follows the sign-in, asks OLX again if it lands on its home page, and catches the HTTPS
 * callback itself.
 *
 * [forceReauth] clears the WebView's cookies first, so OLX serves a real login form.
 * [onDismissed] fires when the seller closes the screen without finishing.
 */
@Composable
private fun rememberInAppOlxAuthLauncher(
    forceReauth: Boolean,
    onDismissed: (OlxAuthDismissReason) -> Unit,
): (String) -> Unit {
    var authUrl by remember { mutableStateOf<String?>(null) }
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val analytics = koinInject<Analytics>()

    authUrl?.let { url ->
        OlxAuthWebViewDialog(
            authorizeUrl = url,
            onCallback = { callbackUrl, client ->
                authUrl = null
                client.logEnded(analytics, outcome = "callback")
                OlxAuthCallbackBridge.publishCallback(callbackUrl)
            },
            onClose = { client ->
                authUrl = null
                client.logEnded(analytics, outcome = "closed")
                currentOnDismissed(OlxAuthDismissReason.UserCancelled)
            },
        )
    }

    return remember(forceReauth) {
        { url: String ->
            if (forceReauth) CookieManager.getInstance().removeAllCookies { authUrl = url } else authUrl = url
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OlxAuthWebViewDialog(
    authorizeUrl: String,
    onCallback: (String, OlxAuthWebViewClient) -> Unit,
    onClose: (OlxAuthWebViewClient) -> Unit,
) {
    val currentOnCallback by rememberUpdatedState(onCallback)
    var waiting by remember { mutableStateOf(false) }
    val client = remember(authorizeUrl) {
        OlxAuthWebViewClient(
            authorizeUrl = authorizeUrl,
            onWaiting = { waiting = it },
            onCallback = { url, self -> currentOnCallback(url, self) },
        )
    }
    Dialog(
        onDismissRequest = { onClose(client) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LoadingOverlay(isLoading = waiting) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = desktopUserAgent(settings.userAgentString)
                        // OLX picks its mobile site from the `Sec-CH-UA-Mobile` client hint, not
                        // only from the user agent: a desktop agent alone still got a 302 to
                        // `m.olx.kz` (traced 2026-11-26).
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                            WebSettingsCompat.setUserAgentMetadata(
                                settings,
                                UserAgentMetadata.Builder(WebSettingsCompat.getUserAgentMetadata(settings))
                                    .setMobile(false)
                                    .setPlatform("Linux")
                                    .build(),
                            )
                        }
                        webViewClient = client
                        loadUrl(authorizeUrl)
                    }
                },
            )
        }
    }
}

private const val AUTHORIZE_DELAY_MS = 5_000L
private const val MAX_RETRIES = 2

private class OlxAuthWebViewClient(
    private val authorizeUrl: String,
    private val onWaiting: (Boolean) -> Unit,
    private val onCallback: (String, OlxAuthWebViewClient) -> Unit,
) : WebViewClient() {
    private var done = false
    private var retries = 0
    private var waiting = false

    /** How often the app held or repeated the request to `/oauth/authorize`. */
    private var waits = 0

    /** The page the seller was on, or null before the first one opened. */
    private var page: OlxLoginPage? = null

    fun logEnded(analytics: Analytics, outcome: String) {
        analytics.logEvent(
            AnalyticsEvents.OLX_LOGIN_WEB_VIEW_ENDED,
            mapOf(
                "outcome" to outcome,
                "page" to if (waiting) "waiting" else page?.analyticsValue ?: "loading",
                "waits" to waits,
                "country" to _currentOlxCountry.code,
            ),
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (interceptCallback(url)) return true
        // OLX's own jump back to the partner page right after the login: hold it (see KDoc above).
        if (request.isForMainFrame && !request.isRedirect && pageOf(url) == OlxLoginPage.Authorize) {
            hold(view, url)
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (interceptCallback(url)) view.stopLoading()
    }

    /** Also runs for in-page navigations, which OLX's newer site uses to show its home page. */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        if (done) return
        page = pageOf(url)
        if (page != OlxLoginPage.OlxHome || waiting || retries >= MAX_RETRIES) return
        retries++
        hold(view, authorizeUrl)
    }

    /** Shows the spinner for [AUTHORIZE_DELAY_MS], then opens [url]; an OLX error page stays visible. */
    private fun hold(view: WebView, url: String) {
        waits++
        setWaiting(true)
        view.postDelayed({
            if (!done) {
                setWaiting(false)
                view.loadUrl(url)
            }
        }, AUTHORIZE_DELAY_MS)
    }

    private fun setWaiting(value: Boolean) {
        waiting = value
        onWaiting(value)
    }

    private fun pageOf(url: String): OlxLoginPage {
        val uri = url.toUri()
        return OlxLoginPage.of(host = uri.host.orEmpty(), path = uri.path.orEmpty())
    }

    private fun interceptCallback(url: String): Boolean {
        if (done) return true
        val isCallback = url.startsWith(OlxConfig.redirectUri) ||
            url.startsWith("${OlxConfig.appCallbackScheme}://")
        if (!isCallback) return false
        done = true
        onCallback(url, this)
        return true
    }
}
