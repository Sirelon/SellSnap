package com.sirelon.sellsnap.features.seller.auth.presentation

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun rememberOlxAuthLauncher(forceReauth: Boolean): (String) -> Unit {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    return remember(context, forceReauth) {
        { url: String ->
            val intent = CustomTabsIntent.Builder().setShowTitle(true).build()
            if (forceReauth) {
                // Force-relogin (TRD route 2 - no WebView without separate product sign-off):
                // there is no public Custom Tabs API to chain "log out, then go to this other
                // URL" inside a single session. This fires OLX's logout URL first and the
                // authorize URL right behind it on the same CustomTabsIntent, which tends to
                // reuse the existing tab rather than stack a second one - but that reuse behavior
                // is UNVERIFIED against a real OLX login session; see the SIR-83 follow-ups doc.
                intent.launchUrl(context, Uri.parse(_currentOlxCountry.logoutUrl))
                coroutineScope.launch {
                    delay(FORCE_REAUTH_LOGOUT_DELAY_MILLIS)
                    intent.launchUrl(context, Uri.parse(url))
                }
            } else {
                intent.launchUrl(context, Uri.parse(url))
            }
        }
    }
}

private const val FORCE_REAUTH_LOGOUT_DELAY_MILLIS = 700L
