package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import com.sirelon.sellsnap.features.seller.auth.data.OlxConfig
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

@Composable
actual fun rememberOlxAuthLauncher(): (String) -> Unit {
    val holder = remember { SessionHolder() }
    return remember {
        { url: String ->
            val nsUrl = NSURL.URLWithString(url) ?: return@remember
            val scheme = OlxConfig.redirectUri.substringBefore("://")
            val callback = ASWebAuthenticationSessionCallback.callbackWithCustomScheme(scheme)
            val session = ASWebAuthenticationSession(
                uRL = nsUrl,
                callback = callback,
                completionHandler = { callbackUrl: NSURL?, _: NSError? ->
                    callbackUrl?.absoluteString?.let { OlxAuthCallbackBridge.publishCallback(it) }
                    holder.session = null
                },
            )
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
