package com.sirelon.sellsnap.features.seller.auth.presentation

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge

/**
 * Force-relogin for add-account/reconnect (D5): a Custom Tabs *ephemeral* session shares no
 * cookies or storage with the browser, so OLX always serves a real login form - the direct
 * Android equivalent of iOS's `prefersEphemeralWebBrowserSession`, and TRD's preferred outcome
 * (keeps the system browser, no WebView, no security-posture sign-off needed).
 * https://developer.chrome.com/docs/android/custom-tabs/guide-ephemeral-tab
 *
 * Requires `androidx.browser:browser` 1.9.0+. No runtime support check: an unrecognised Custom
 * Tabs extra is simply ignored by providers that don't understand it, so a provider that doesn't
 * support ephemeral browsing just falls back to a normal tab on its own - nothing to branch on
 * here. If that happens, the seller may land back on the already-connected account, but
 * SellerAccountRepository.addAccount's dedupe now handles that gracefully (a precise "already
 * connected as X" message, never a duplicate entry).
 *
 * [onDismissed] fires when the seller comes back from the tab without finishing the login.
 * `launchUrl` reports nothing when the tab closes, so [OlxAuthReturnTracker] infers it from the
 * app resuming with no new OLX callback (SIR-123).
 */
@Composable
actual fun rememberOlxAuthLauncher(forceReauth: Boolean, onDismissed: () -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val tracker = remember { OlxAuthReturnTracker { OlxAuthCallbackBridge.publishedCount } }
    val currentOnDismissed by rememberUpdatedState(onDismissed)

    LifecycleResumeEffect(tracker) {
        if (tracker.onResumed()) currentOnDismissed()
        onPauseOrDispose { }
    }

    return remember(context, forceReauth, tracker) {
        { url: String ->
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setEphemeralBrowsingEnabled(forceReauth)
                .build()
            tracker.onLaunched()
            intent.launchUrl(context, Uri.parse(url))
        }
    }
}
