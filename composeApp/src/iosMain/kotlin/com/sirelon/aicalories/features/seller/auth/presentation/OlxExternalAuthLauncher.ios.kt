package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.data.OlxConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
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
 * Runs the OLX login in an in-app WKWebView instead of an ASWebAuthenticationSession.
 *
 * Why (SIR-195, `.claude/rules/olx-api.md`): a phone is sent to `m.olx.<tld>`, logs in there, and
 * OLX's page jumps straight back to `www.olx.<tld>/oauth/authorize`. Arriving within about a
 * second of the login, `/oauth/authorize` rejects the brand-new session and lands the seller on
 * the OLX home page; five seconds later the same request redirects to our `redirect_uri` with a
 * code (Inferred: from tests on olx.ro, 2026-11-22). An auth session cannot hold that jump, so the
 * app owns the web view: it delays the jump, retries if OLX still lands on its home page, and
 * catches the HTTPS callback itself. Same logic as the Android launcher.
 *
 * [forceReauth] uses a non-persistent data store, so OLX serves a real login form.
 * [onDismissed] fires when the seller closes the screen without finishing.
 */
@Composable
actual fun rememberOlxAuthLauncher(
    forceReauth: Boolean,
    onDismissed: (OlxAuthDismissReason) -> Unit,
): (String) -> Unit {
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    return remember(forceReauth) {
        { url: String ->
            val controller = OlxAuthViewController(
                authorizeUrl = url,
                ephemeral = forceReauth,
                onDismissed = { currentOnDismissed(it) },
            )
            if (!controller.present()) currentOnDismissed(OlxAuthDismissReason.LaunchFailed)
        }
    }
}

private const val AUTHORIZE_DELAY_MS = 5_000L
private const val MAX_RETRIES = 2

@OptIn(ExperimentalForeignApi::class)
private class OlxAuthViewController(
    private val authorizeUrl: String,
    private val ephemeral: Boolean,
    private val onDismissed: (OlxAuthDismissReason) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol, UIAdaptivePresentationControllerDelegateProtocol {

    private val content = UIViewController()
    private val navigation = UINavigationController(rootViewController = content)
    private val spinner = UIActivityIndicatorView(activityIndicatorStyle = UIActivityIndicatorViewStyleLarge)
    private lateinit var webView: WKWebView
    private var done = false
    private var retries = 0

    /** True once the seller is back on an `m.olx.<tld>` page, so OLX's next jump is held. */
    private var holdNextAuthorize = false

    fun present(): Boolean {
        @Suppress("UNCHECKED_CAST")
        val window = (UIApplication.sharedApplication.windows as List<UIWindow>).firstOrNull { it.isKeyWindow() }
        var top = window?.rootViewController ?: return false
        while (top.presentedViewController != null) top = top.presentedViewController!!

        val configuration = WKWebViewConfiguration()
        if (ephemeral) configuration.websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
        webView = WKWebView(frame = CGRectZero.readValue(), configuration = configuration)
        webView.navigationDelegate = this
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
            retainedControllers -= this
            onDismissed(OlxAuthDismissReason.UserCancelled)
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
            isMainFrame && holdNextAuthorize && isAuthorize(url) -> {
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
        val url = NSURL(string = webView.URL?.absoluteString.orEmpty())
        val host = url.host.orEmpty()
        if (host.startsWith("m.olx.")) holdNextAuthorize = true
        val onHome = (host.startsWith("m.olx.") || host.startsWith("www.olx.")) &&
            url.path.orEmpty().trim('/').isEmpty()
        if (onHome && retries < MAX_RETRIES) {
            retries++
            holdNextAuthorize = false
            later { load(authorizeUrl) }
        }
    }

    private fun load(url: String) {
        if (done) return
        spinner.stopAnimating()
        NSURL.URLWithString(url)?.let { webView.loadRequest(NSURLRequest.requestWithURL(it)) }
    }

    private fun later(block: () -> Unit) {
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
        retainedControllers -= this
        if (callbackUrl != null) {
            OlxAuthCallbackBridge.publishCallback(callbackUrl)
        } else {
            onDismissed(OlxAuthDismissReason.UserCancelled)
        }
    }

    private fun isCallback(url: String): Boolean =
        url.startsWith(OlxConfig.redirectUri) || url.startsWith("${OlxConfig.appCallbackScheme}://")

    private fun isAuthorize(url: String): Boolean {
        val parsed = NSURL(string = url)
        return parsed.host.orEmpty().startsWith("www.olx.") &&
            parsed.path.orEmpty().trimEnd('/').endsWith("/oauth/authorize")
    }

}

/** UIKit holds delegates weakly; keeps each login controller alive until its login ends. */
private val retainedControllers = mutableSetOf<Any>()
