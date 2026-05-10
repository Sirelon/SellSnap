package com.sirelon.sellsnap.features.seller.auth.presentation

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private class OlxWebViewHolder(
    val redirectUri: String,
    var onUrlIntercepted: (String) -> Unit,
)

@Composable
actual fun OlxAuthWebView(
    url: String,
    redirectUri: String,
    onUrlIntercepted: (String) -> Unit,
    modifier: Modifier,
) {
    val holder = remember(redirectUri) { OlxWebViewHolder(redirectUri, onUrlIntercepted) }
    holder.onUrlIntercepted = onUrlIntercepted

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val reqUrl = request.url.toString()

                        if (reqUrl.contains("m.olx.ua")) {
                            val fixed = reqUrl.replace("m.olx.ua", "www.olx.ua")
                            view.loadUrl(fixed)
                            return true
                        }

                        if (reqUrl.startsWith(holder.redirectUri) &&
                            (request.url.getQueryParameter("code") != null ||
                                request.url.getQueryParameter("error") != null)
                        ) {
                            holder.onUrlIntercepted(reqUrl)
                            return true
                        }

                        return false
                    }
                }

                loadUrl(url)
            }
        },
        modifier = modifier,
    )
}
