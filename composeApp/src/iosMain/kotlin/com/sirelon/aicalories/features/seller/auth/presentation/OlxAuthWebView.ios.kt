package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.javaScriptEnabled
import platform.darwin.NSObject

private class OlxWKNavigationDelegate(
    val redirectUri: String,
    var onUrlIntercepted: (String) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()

        if (url.contains("m.olx.ua")) {
            val fixed = url.replace("m.olx.ua", "www.olx.ua")
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            NSURL.URLWithString(fixed)?.let { nsUrl ->
                webView.loadRequest(NSURLRequest.requestWithURL(nsUrl))
            }
            return
        }

        if (url.startsWith(redirectUri)) {
            val nsUrl = decidePolicyForNavigationAction.request.URL
            val hasCode = nsUrl?.query?.contains("code=") == true
            val hasError = nsUrl?.query?.contains("error=") == true
            if (hasCode || hasError) {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                onUrlIntercepted(url)
                return
            }
        }

        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
    }
}

@Composable
actual fun OlxAuthWebView(
    url: String,
    redirectUri: String,
    onUrlIntercepted: (String) -> Unit,
    modifier: Modifier,
) {
    val delegate = remember(redirectUri) {
        OlxWKNavigationDelegate(redirectUri, onUrlIntercepted)
    }
    delegate.onUrlIntercepted = onUrlIntercepted

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration()
            config.preferences.javaScriptEnabled = true

            WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                navigationDelegate = delegate
                customUserAgent =
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                NSURL.URLWithString(url)?.let { nsUrl ->
                    loadRequest(NSURLRequest.requestWithURL(nsUrl))
                }
            }
        },
        modifier = modifier,
    )
}
