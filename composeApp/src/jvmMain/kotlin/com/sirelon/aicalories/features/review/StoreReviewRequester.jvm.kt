package com.sirelon.sellsnap.features.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** No app store to rate in. */
@Composable
actual fun rememberStoreReviewRequester(): () -> Unit = remember { {} }
