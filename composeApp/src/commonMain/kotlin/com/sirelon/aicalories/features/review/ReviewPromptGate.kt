package com.sirelon.sellsnap.features.review

/**
 * Whether to ask the platform for a store-review prompt right now.
 *
 * The success signal is the publish itself: OLX accepted the advert and the seller is looking at
 * the screen that says so. Nothing here reads the returned `AdvertStatus` - an advert sitting in
 * moderation is the normal outcome of a successful post, not a lesser one, and a whitelist of
 * "good" statuses only re-encodes a guess about OLX's vocabulary as a silent refusal to ever ask.
 *
 * There is no third option: the native APIs take a request and report nothing back - not whether
 * the sheet appeared, not whether a rating was left. Apple caps the prompt at 3 per app per device
 * per 365 days and may show nothing at all; Google Play applies an undisclosed quota and shows
 * nothing in debug or sideloaded builds. Every request is therefore spent blind, which is why the
 * gate below is a conjunction rather than a heuristic.
 *
 * Sources:
 * - https://developer.apple.com/app-store/ratings-and-reviews/ (request limits; custom review
 *   prompts are not permitted, the system API must be used)
 * - https://developer.android.com/guide/playcore/in-app-review (quota; no pre-prompt, no custom
 *   trigger button, no design overlay)
 */
sealed interface ReviewPromptDecision {
    data object Request : ReviewPromptDecision
    data class Skip(val reason: ReviewPromptSkipReason) : ReviewPromptDecision
}

/**
 * Why a request was withheld. Logged as `reason` on `review_prompt_skipped`, and
 * the only diagnostic available for a feature whose success is unobservable: if
 * [Request][ReviewPromptDecision.Request] never happens, the dominant reason says whether the
 * publish funnel, publish reliability, or the gate itself is responsible.
 */
enum class ReviewPromptSkipReason(val analyticsValue: String) {
    TooFewPublishes("too_few_publishes"),
    InstallSession("install_session"),
    RecentError("recent_error"),
    WhatsNew("whats_new"),
    Cooldown("cooldown"),
}

/**
 * 130 days rather than a lifetime attempt counter: three requests spaced this far apart cannot fit
 * inside one 365-day window, so Apple's cap is respected by construction and nothing needs to
 * count attempts.
 */
const val ReviewPromptCooldownSeconds: Long = 130L * 24 * 60 * 60

/** Adverts a returning seller must have published before being asked. */
const val ReviewPromptMinPublishes: Int = 2

/**
 * Adverts a seller who has never re-opened the app must have published instead. Coming back is the
 * stronger signal - someone who publishes twice and never returns is the worst rater to spend a
 * request on - but three listings in one sitting is real work, and without this branch the gate
 * can sit dark indefinitely at low install volume.
 */
const val ReviewPromptMinPublishesInInstallSession: Int = 3

fun reviewPromptDecision(
    publishCount: Int,
    isReturningSession: Boolean,
    hadPublishErrorThisSession: Boolean,
    whatsNewShownThisSession: Boolean,
    lastPromptEpochSeconds: Long?,
    nowEpochSeconds: Long,
): ReviewPromptDecision {
    if (publishCount < ReviewPromptMinPublishes) {
        return ReviewPromptDecision.Skip(ReviewPromptSkipReason.TooFewPublishes)
    }
    if (!isReturningSession && publishCount < ReviewPromptMinPublishesInInstallSession) {
        return ReviewPromptDecision.Skip(ReviewPromptSkipReason.InstallSession)
    }
    if (hadPublishErrorThisSession) {
        return ReviewPromptDecision.Skip(ReviewPromptSkipReason.RecentError)
    }
    if (whatsNewShownThisSession) {
        return ReviewPromptDecision.Skip(ReviewPromptSkipReason.WhatsNew)
    }
    if (lastPromptEpochSeconds != null &&
        nowEpochSeconds - lastPromptEpochSeconds < ReviewPromptCooldownSeconds
    ) {
        return ReviewPromptDecision.Skip(ReviewPromptSkipReason.Cooldown)
    }
    return ReviewPromptDecision.Request
}
