package com.sirelon.sellsnap.features.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Delay before asking, measured from the success screen appearing. Long enough that the prompt
 * lands on a settled screen the seller has read rather than on top of its entrance animation,
 * short enough that they are still there.
 */
private const val ReviewPromptDelayMillis = 2_000L

/**
 * Asks for the store-review prompt on the publish-success screen, if [ReviewPromptCoordinator]
 * says this seller and this advert qualify.
 *
 * Leaving the screen cancels the delay, so a seller who taps straight through is never asked. The
 * resumed check covers the remaining gap: "View on OLX" opens a browser over the app, and the
 * composable is not removed until the transition finishes, so without it a tap at 1.8s would spend
 * a request while the app is in the background and nothing could be shown.
 *
 * One publish gets one decision. Without the saved flag, rotating the device re-runs this and logs
 * a second outcome for the same advert - and after process death the screen is restored with the
 * session flags cleared and `isReturningSession` now true, so a publish that was correctly skipped
 * would be asked about on a screen the seller is only re-entering.
 */
@Composable
fun StoreReviewPromptEffect() {
    val coordinator: ReviewPromptCoordinator = koinInject()
    val requestReview = rememberStoreReviewRequester()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var decided by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (decided) return@LaunchedEffect
        delay(ReviewPromptDelayMillis)
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        decided = true
        if (coordinator.requestIfEligible()) {
            requestReview()
        }
    }
}
