package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable

/** Why the OLX login closed without a callback - the `reason` of `auth_abandoned`. */
enum class OlxAuthDismissReason(val analyticsValue: String) {
    /** The seller closed it: iOS `canceledLogin`, Android back from the Custom Tab with no callback. */
    UserCancelled("user_cancelled"),

    /**
     * The system ended it: any other `ASWebAuthenticationSession` error (presentation context
     * missing or invalid; Android has no equivalent signal), or the process was killed while the
     * login was open, reported on the next start.
     */
    SystemCancelled("system_cancelled"),
}

/**
 * [forceReauth] forces a fresh OLX login (no silently-reused session) for add-account and
 * reconnect flows, so a seller isn't re-authenticated straight back into the account they're
 * trying to add a second one alongside, or the dead one they're trying to reconnect. First-connect
 * (from the guest/landing flow) should leave it false - the default.
 *
 * [onDismissed] fires when the auth flow closes without an OLX callback (SIR-123) - never on a real
 * auth failure - and carries the [OlxAuthDismissReason]. iOS: `ASWebAuthenticationSession` reports
 * `ASWebAuthenticationSessionErrorCodeCanceledLogin` on cancel, which maps to
 * [OlxAuthDismissReason.UserCancelled]; any other completion error maps to
 * [OlxAuthDismissReason.SystemCancelled]. Android: Custom Tabs report nothing on close, so it is
 * inferred from the app resuming with no OLX callback ([OlxAuthReturnTracker]) and always reported
 * as [OlxAuthDismissReason.UserCancelled]. Web/desktop open a browser tab with no way back to this
 * app, so [onDismissed] is never invoked there.
 */
@Composable
expect fun rememberOlxAuthLauncher(
    forceReauth: Boolean = false,
    onDismissed: (OlxAuthDismissReason) -> Unit = {},
): (String) -> Unit
