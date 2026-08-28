package com.sirelon.sellsnap.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.koinInject

/** Call once per NavDisplay/backstack, passing its current top destination. */
@Composable
fun TrackScreenViews(currentScreen: AnalyticsScreen?) {
    val analytics: Analytics = koinInject()
    LaunchedEffect(currentScreen) {
        currentScreen?.let { analytics.logScreenView(it.screenName) }
    }
}
