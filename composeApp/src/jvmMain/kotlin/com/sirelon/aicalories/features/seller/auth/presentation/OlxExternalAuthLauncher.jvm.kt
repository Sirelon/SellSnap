package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

// forceReauth is a no-op on desktop: opening the system browser is already first-connect-equivalent
// behavior (no in-app session to force-clear). onDismissed is never invoked: opening the system
// browser returns control immediately, with no signal for the tab being closed (SIR-123).
@Composable
actual fun rememberOlxAuthLauncher(forceReauth: Boolean, onDismissed: () -> Unit): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) { { url: String -> uriHandler.openUri(url) } }
}
