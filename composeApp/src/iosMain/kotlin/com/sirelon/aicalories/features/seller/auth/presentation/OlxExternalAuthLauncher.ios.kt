package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.data.OlxConfig
import com.sirelon.sellsnap.features.seller.auth.data.OlxLoginModeRepository
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import org.koin.compose.koinInject
import platform.CoreGraphics.CGRectZero
import platform.Foundation.HTTPMethod
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.UIActivityIndicatorViewStyleLarge
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UINavigationController
import platform.UIKit.UIPresentationController
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/**
 * Picks the OLX login screen from the Firestore switch `config/olx_login.systemBrowser`
 * (OlxLoginModeRepository): an `ASWebAuthenticationSession` when on, otherwise the in-app
 * WKWebView below. Until the switch is read, the in-app WKWebView is used.
 */
@Composable
actual fun rememberOlxAuthLauncher(
    forceReauth: Boolean,
    onDismissed: (OlxAuthDismissReason) -> Unit,
): (String) -> Unit {
    val loginMode = koinInject<OlxLoginModeRepository>()
    val systemBrowser by produceState(initialValue = false, loginMode) { value = loginMode.useSystemBrowser() }
    val inApp = rememberInAppOlxAuthLauncher(forceReauth, onDismissed)
    val browser = rememberSystemBrowserOlxAuthLauncher(forceReauth, onDismissed)
    return remember(systemBrowser, inApp, browser) { if (systemBrowser) browser else inApp }
}

/**
 * The OLX login in an in-app WKWebView instead of an ASWebAuthenticationSession.
 *
 * Why (SIR-195, `.claude/rules/olx-api.md`): a phone is sent to `m.olx.<tld>`, logs in there, and
 * OLX's page jumps straight back to `www.olx.<tld>/oauth/authorize`. Arriving within about a
 * second of the login, `/oauth/authorize` rejects the brand-new session and lands the seller on
 * the OLX home page; five seconds later the same request redirects to our `redirect_uri` with a
 * code (Inferred: from tests on olx.ro, 2026-11-22). An auth session cannot hold that jump, so the
 * app owns the web view. It sends a desktop Safari user agent, which keeps the whole login on
 * `www` and avoids the `m.olx` jump (`desktopUserAgent`, SIR-203). It still delays any jump to
 * `/oauth/authorize` that follows the sign-in, asks OLX again if it lands on its home page, and
 * catches the HTTPS callback itself. Same logic as the Android launcher.
 *
 * [forceReauth] uses a non-persistent data store, so OLX serves a real login form.
 * [onDismissed] fires when the seller closes the screen without finishing.
 */
@Composable
private fun rememberInAppOlxAuthLauncher(
    forceReauth: Boolean,
    onDismissed: (OlxAuthDismissReason) -> Unit,
): (String) -> Unit {
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val analytics = koinInject<Analytics>()
    return remember(forceReauth, analytics) {
        { url: String ->
            val controller = OlxAuthViewController(
                authorizeUrl = url,
                ephemeral = forceReauth,
                analytics = analytics,
                onDismissed = { currentOnDismissed(it) },
            )
            if (!controller.present()) currentOnDismissed(OlxAuthDismissReason.LaunchFailed)
        }
    }
}

private const val AUTHORIZE_DELAY_MS = 5_000L
private const val MAX_RETRIES = 2

/**
 * Safari on a Mac. Inferred: OLX picks its mobile site from the user agent; with this one the
 * login stays on `www` (traced with the Android WebView's desktop Chrome agent, see
 * `desktopUserAgent`).
 */
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"

@OptIn(ExperimentalForeignApi::class)
private class OlxAuthViewController(
    private val authorizeUrl: String,
    private val ephemeral: Boolean,
    private val analytics: Analytics,
    private val onDismissed: (OlxAuthDismissReason) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {

    private val content = UIViewController()
    private val navigation = UINavigationController(rootViewController = content)
    private val spinner = UIActivityIndicatorView(activityIndicatorStyle = UIActivityIndicatorViewStyleLarge)
    private lateinit var webView: WKWebView
    private var urlWatch: NSTimer? = null
    private var done = false
    private var retries = 0
    private var waiting = false

    /** How often the app held or repeated the request to `/oauth/authorize`. */
    private var waits = 0

    /** The page the seller was on, or null before the first one opened. */
    private var page: OlxLoginPage? = null

    /** True once a sign-in page off `www` has loaded, so OLX's next jump to `/oauth/authorize` is held. */
    private var holdNextAuthorize = false

    fun present(): Boolean {
        @Suppress("UNCHECKED_CAST")
        val window = (UIApplication.sharedApplication.windows as List<UIWindow>).firstOrNull { it.isKeyWindow() }
        var top = window?.rootViewController ?: return false
        while (top.presentedViewController != null) top = top.presentedViewController!!

        val configuration = WKWebViewConfiguration()
        if (ephemeral) configuration.websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
        webView = WKWebView(frame = CGRectZero.readValue(), configuration = configuration)
        webView.customUserAgent = DESKTOP_USER_AGENT
        webView.navigationDelegate = this
        // Polls the address because OLX's newer site shows its home page with an in-page
        // navigation, which no navigation delegate call reports.
        urlWatch = NSTimer.scheduledTimerWithTimeInterval(interval = 0.5, repeats = true) { _ -> checkPage() }
        webView.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        webView.setFrame(content.view.bounds)
        content.view.addSubview(webView)
        spinner.center = content.view.center
        spinner.hidesWhenStopped = true
        content.view.addSubview(spinner)
        content.navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemCancel,
            target = this,
            action = platform.objc.sel_registerName("cancel"),
        )
        navigation.presentationController?.delegate = this

        top.presentViewController(navigation, animated = true, completion = null)
        load(authorizeUrl)
        retainedControllers += this
        return true
    }

    @kotlinx.cinterop.ObjCAction
    fun cancel() = finish(callbackUrl = null)

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        if (!done) {
            done = true
            release()
            logEnded(outcome = "closed")
            onDismissed(OlxAuthDismissReason.UserCancelled)
        }
    }

    private fun checkPage() {
        if (done) return
        val url = webView.URL ?: return
        page = OlxLoginPage.of(host = url.host.orEmpty(), path = url.path.orEmpty())
        if (page == OlxLoginPage.OlxHome && !waiting && retries < MAX_RETRIES) {
            retries++
            holdNextAuthorize = false
            later { load(authorizeUrl) }
        }
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
        val isMainFrame = decidePolicyForNavigationAction.targetFrame?.isMainFrame() ?: true
        when {
            isCallback(url) -> {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                finish(callbackUrl = url)
            }
            // OLX's own jump back to the partner page right after the login: hold it (see KDoc).
            isMainFrame && holdNextAuthorize && isAuthorize(url) &&
                decidePolicyForNavigationAction.request.HTTPMethod == "GET" -> {
                holdNextAuthorize = false
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                later { load(url) }
            }
            else -> decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        if (done) return
        val url = webView.URL ?: return
        val host = url.host.orEmpty()
        val signIn = OlxLoginPage.of(host = host, path = url.path.orEmpty()) == OlxLoginPage.SignIn
        if (signIn && !host.startsWith("www.")) holdNextAuthorize = true
    }

    private fun load(url: String) {
        if (done) return
        waiting = false
        spinner.stopAnimating()
        NSURL.URLWithString(url)?.let { webView.loadRequest(NSURLRequest.requestWithURL(it)) }
    }

    private fun later(block: () -> Unit) {
        waits++
        waiting = true
        spinner.startAnimating()
        content.view.bringSubviewToFront(spinner)
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, AUTHORIZE_DELAY_MS * NSEC_PER_MSEC.toLong()),
            dispatch_get_main_queue(),
        ) { if (!done) block() }
    }

    private fun finish(callbackUrl: String?) {
        if (done) return
        done = true
        navigation.dismissViewControllerAnimated(true, completion = null)
        release()
        logEnded(outcome = if (callbackUrl != null) "callback" else "closed")
        if (callbackUrl != null) {
            OlxAuthCallbackBridge.publishCallback(callbackUrl)
        } else {
            onDismissed(OlxAuthDismissReason.UserCancelled)
        }
    }

    private fun release() {
        urlWatch?.invalidate()
        urlWatch = null
        retainedControllers -= this
    }

    private fun logEnded(outcome: String) {
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

    private fun isCallback(url: String): Boolean =
        url.startsWith(OlxConfig.redirectUri) || url.startsWith("${OlxConfig.appCallbackScheme}://")

    private fun isAuthorize(url: String): Boolean {
        val parsed = NSURL(string = url)
        return OlxLoginPage.of(host = parsed.host.orEmpty(), path = parsed.path.orEmpty()) == OlxLoginPage.Authorize
    }

}

/** UIKit holds delegates weakly; keeps each login controller alive until its login ends. */
private val retainedControllers = mutableSetOf<Any>()
