package com.sirelon.sellsnap.features.review

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import kotlin.time.Clock

/**
 * Set to true to make every publish ask for the store-review prompt - never commit as true.
 *
 * The gate normally needs two or three published adverts and a returning session, which cannot be
 * reached without posting real adverts to a real OLX account. With this on, the prompt is requested
 * on the next successful publish and the cooldown is not written, so it can be triggered again.
 * On an iOS development build the sheet genuinely appears; on Android it never renders outside a
 * Play-installed build, so there the only proof is the `review_prompt_requested` log line.
 */
internal var reviewPromptAlwaysRequest = false

/**
 * Owns the store-review decision: the persisted counters, the flags that must not outlive the
 * process, and the analytics that make an otherwise invisible feature diagnosable.
 *
 * Registered as a Koin `single`, which in this app is process-lifetime - Koin is started by the
 * `KoinApplication` composable and never stopped, so these flags survive Activity recreation and
 * reset only on a cold start, which is exactly the "session" they are named for.
 */
class ReviewPromptCoordinator(
    private val store: ReviewPromptStore,
    private val analytics: Analytics,
) {
    /**
     * False until the app has been opened at least once before. Set from
     * `AppNavigationViewModel.resolveStartupDestination`, which already reads the onboarding
     * marker to decide where to send the seller - the same read answers this question.
     */
    var isReturningSession: Boolean = false

    /**
     * A publish failed earlier in this session. A seller who has just been shown an error is not
     * asked for a rating, even if the next attempt works.
     */
    var hadPublishErrorThisSession: Boolean = false

    /** The What's New sheet was shown this session; one interruption per session is enough. */
    var whatsNewShownThisSession: Boolean = false

    suspend fun onPublishSucceeded() {
        store.incrementPublishCount()
    }

    fun onPublishFailed() {
        hadPublishErrorThisSession = true
    }

    /**
     * Decides, records, and reports. Returns true when the caller should invoke the platform
     * requester.
     *
     * The cooldown is written *before* the platform call rather than after, because the call
     * reports nothing: if the request is made and the process then dies, an unwritten timestamp
     * would let the next publish spend another one of Apple's three yearly slots for the same
     * moment. Writing first can at worst cost one unmade request.
     */
    suspend fun requestIfEligible(status: AdvertStatus): Boolean {
        if (reviewPromptAlwaysRequest) return true

        // DataStore throws on a corrupt or unreadable file and this runs inside a LaunchedEffect,
        // where an escaping exception takes the process down - two seconds after telling the seller
        // their advert is live. A rating prompt is never worth a crash, so a storage failure is
        // read as "no history" and a failed write as "do not ask".
        val publishCount = runCatching { store.publishCount() }.getOrNull() ?: return false
        val lastPrompt = runCatching { store.lastPromptEpochSeconds() }.getOrNull()
        val now = nowEpochSeconds()
        val decision = reviewPromptDecision(
            publishCount = publishCount,
            status = status,
            isReturningSession = isReturningSession,
            hadPublishErrorThisSession = hadPublishErrorThisSession,
            whatsNewShownThisSession = whatsNewShownThisSession,
            lastPromptEpochSeconds = lastPrompt,
            nowEpochSeconds = now,
        )
        return when (decision) {
            is ReviewPromptDecision.Skip -> {
                analytics.logEvent(
                    AnalyticsEvents.REVIEW_PROMPT_SKIPPED,
                    mapOf("reason" to decision.reason.analyticsValue),
                )
                false
            }

            ReviewPromptDecision.Request -> {
                runCatching { store.markPromptRequested(now) }.getOrElse { return false }
                analytics.logEvent(
                    AnalyticsEvents.REVIEW_PROMPT_REQUESTED,
                    mapOf(
                        "publish_count" to publishCount,
                        "returning_session" to isReturningSession,
                    ),
                )
                true
            }
        }
    }

    private fun nowEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000
}
