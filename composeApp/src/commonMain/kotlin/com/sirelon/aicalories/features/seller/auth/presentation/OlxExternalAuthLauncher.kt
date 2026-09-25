package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable

/**
 * [forceReauth] forces a fresh OLX login (no silently-reused session) for add-account and
 * reconnect flows, so a seller isn't re-authenticated straight back into the account they're
 * trying to add a second one alongside, or the dead one they're trying to reconnect. First-connect
 * (from the guest/landing flow) should leave it false - the default.
 *
 * [onDismissed] fires when the seller closes the auth flow without completing or failing login
 * (SIR-123) - never on a real auth failure. Reliable on iOS only: its
 * `ASWebAuthenticationSession` reports `ASWebAuthenticationSessionErrorCodeCanceledLogin` on
 * cancel, distinct from any other completion error. Chrome Custom Tabs (Android) and opening a
 * browser tab (web/desktop) return control to the caller as soon as the external browser opens,
 * with no signal for the tab being closed, so [onDismissed] is never invoked on those platforms
 * today.
 */
@Composable
expect fun rememberOlxAuthLauncher(forceReauth: Boolean = false, onDismissed: () -> Unit = {}): (String) -> Unit
