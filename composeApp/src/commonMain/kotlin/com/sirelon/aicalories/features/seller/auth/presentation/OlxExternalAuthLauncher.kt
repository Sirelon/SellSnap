package com.sirelon.sellsnap.features.seller.auth.presentation

import androidx.compose.runtime.Composable

/**
 * [forceReauth] forces a fresh OLX login (no silently-reused session) for add-account and
 * reconnect flows, so a seller isn't re-authenticated straight back into the account they're
 * trying to add a second one alongside, or the dead one they're trying to reconnect. First-connect
 * (from the guest/landing flow) should leave it false - the default.
 */
@Composable
expect fun rememberOlxAuthLauncher(forceReauth: Boolean = false): (String) -> Unit
