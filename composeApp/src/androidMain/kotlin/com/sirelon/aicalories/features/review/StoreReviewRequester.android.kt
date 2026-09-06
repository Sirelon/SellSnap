package com.sirelon.sellsnap.features.review

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.sirelon.sellsnap.di.applicationScopeQualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Google Play In-App Review.
 *
 * Displays nothing unless the app was installed by Google Play - a local `assembleDebug` install
 * never qualifies, an internal-test-track install does - and nothing once the app's undisclosed
 * quota for this user is spent. Both cases complete normally, so a silent run here is an expected
 * outcome rather than a failure.
 *
 * https://developer.android.com/guide/playcore/in-app-review
 * https://developer.android.com/guide/playcore/in-app-review/test
 */
@Composable
actual fun rememberStoreReviewRequester(): () -> Unit {
    val activity = LocalActivity.current
    // Deliberately not rememberCoroutineScope(): requestReview() is a round-trip to Play, and the
    // caller has already spent this device's 130-day cooldown by the time it starts. On the
    // composition's scope a tap on "Create another" a few hundred milliseconds later cancels the
    // job between the two calls, and the cooldown buys nothing at all.
    val applicationScope: CoroutineScope = koinInject(applicationScopeQualifier)
    return remember(activity, applicationScope) {
        {
            if (activity != null) {
                // The application scope is Dispatchers.Default; launchReviewFlow puts UI on screen.
                applicationScope.launch(Dispatchers.Main) {
                    // A ReviewException and any Play Services failure are alike here: nothing the
                    // seller can act on, behind a screen that has already told them their advert
                    // is live.
                    runCatching {
                        val manager = ReviewManagerFactory.create(activity)
                        manager.launchReview(activity, manager.requestReview())
                    }
                }
            }
        }
    }
}
