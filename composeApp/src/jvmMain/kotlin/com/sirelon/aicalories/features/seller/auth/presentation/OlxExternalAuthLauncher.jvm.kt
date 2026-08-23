package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

// forceReauth is a no-op on desktop: opening the system browser is already first-connect-equivalent
// behavior (no in-app session to force-clear).
@Composable
actual fun rememberOlxAuthLauncher(forceReauth: Boolean): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) { { url: String -> uriHandler.openUri(url) } }
}
