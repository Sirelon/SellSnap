package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.data.OlxConfig
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

@Composable
actual fun rememberOlxAuthLauncher(forceReauth: Boolean, onDismissed: () -> Unit): (String) -> Unit {
    val holder = remember { SessionHolder() }
    // onDismissed is a fresh lambda from the caller on every recomposition (App.kt closes over
    // `analytics`) - read it through a Composable-updated reference instead of a remember key, so
    // it never invalidates/rebuilds the launcher closure below.
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    return remember(forceReauth) {
        { url: String ->
            val nsUrl = NSURL.URLWithString(url) ?: return@remember
            val scheme = OlxConfig.redirectUri.substringBefore("://")
            val callback = ASWebAuthenticationSessionCallback.callbackWithCustomScheme(scheme)
            val session = ASWebAuthenticationSession(
                uRL = nsUrl,
                callback = callback,
                completionHandler = { callbackUrl: NSURL?, error: NSError? ->
                    val resultUrl = callbackUrl?.absoluteString
                    when {
                        resultUrl != null -> OlxAuthCallbackBridge.publishCallback(resultUrl)
                        // Only a genuine user cancel counts as abandoned - any other failure
                        // (presentation context, network) is a real error, not left silently
                        // unresolved by SIR-123's new dismiss handling.
                        error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> currentOnDismissed()
                        else -> Unit
                    }
                    holder.session = null
                },
            )
            // Add-account/reconnect (forceReauth = true) must not silently reuse OLX's shared
            // web session cookie - otherwise the login screen never appears and the seller is
            // bounced straight back into whichever account is already logged in on olx.*.
            session.prefersEphemeralWebBrowserSession = forceReauth
            @Suppress("UNCHECKED_CAST")
            val keyWindow = (UIApplication.sharedApplication.windows as List<UIWindow>)
                .firstOrNull { it.isKeyWindow() }
            if (keyWindow != null) {
                session.presentationContextProvider = OlxAuthContextProvider(keyWindow)
            }
            holder.session = session
            session.start()
        }
    }
}

private class SessionHolder {
    var session: ASWebAuthenticationSession? = null
}

private class OlxAuthContextProvider(
    private val window: UIWindow,
) : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): UIWindow = window
}
