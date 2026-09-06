package com.sirelon.sellsnap.features.review

import androidx.compose.runtime.Composable

/**
 * Asks the platform to show its own store-review prompt. Composable-scoped because Google Play's
 * flow needs the hosting Activity, which is only reachable from composition.
 *
 * The returned lambda reports nothing and never throws: neither platform says whether the sheet
 * appeared, and both routinely show nothing at all (quota, debug build, sideloaded install). Treat
 * calling it as the whole of the outcome.
 */
@Composable
expect fun rememberStoreReviewRequester(): () -> Unit
