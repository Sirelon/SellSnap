package com.sirelon.sellsnap.features.seller.auth.presentation

import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// OLX redirects mobile user-agents from www.olx.ua to m.olx.ua which has no /oauth/authorize path.
// Passing a desktop UA header prevents the redirect.
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

@Composable
actual fun rememberOlxAuthLauncher(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String ->
            val headers = Bundle().apply { putString("User-Agent", DESKTOP_UA) }
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .apply { intent.putExtra(android.provider.Browser.EXTRA_HEADERS, headers) }
                .launchUrl(context, Uri.parse(url))
        }
    }
}
