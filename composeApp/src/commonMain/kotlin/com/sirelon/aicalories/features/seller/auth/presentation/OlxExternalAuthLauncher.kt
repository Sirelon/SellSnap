package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable

/**
 * [forceReauth] forces a fresh OLX login (no silently-reused session) for add-account and
 * reconnect flows, so a seller isn't re-authenticated straight back into the account they're
 * trying to add a second one alongside, or the dead one they're trying to reconnect. First-connect
 * (from the guest/landing flow) should leave it false - the default.
 *
 * [onDismissed] fires when the seller closes the auth flow without completing or failing login
 * (SIR-123) - never on a real auth failure. iOS: `ASWebAuthenticationSession` reports
 * `ASWebAuthenticationSessionErrorCodeCanceledLogin` on cancel, distinct from any other completion
 * error. Android: Custom Tabs report nothing on close, so it is inferred from the app resuming
 * with no OLX callback ([OlxAuthReturnTracker]). Web/desktop open a browser tab with no way back
 * to this app, so [onDismissed] is never invoked there.
 */
@Composable
expect fun rememberOlxAuthLauncher(forceReauth: Boolean = false, onDismissed: () -> Unit = {}): (String) -> Unit
