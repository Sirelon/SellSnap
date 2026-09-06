package com.sirelon.sellsnap.features.review

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

/**
 * Whether to ask the platform for a store-review prompt right now.
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
    BadStatus("bad_status"),
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
    status: AdvertStatus,
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
    if (!status.isPublishCelebration) {
        return ReviewPromptDecision.Skip(ReviewPromptSkipReason.BadStatus)
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

/**
 * Whether the advert OLX just accepted is something to be pleased about.
 *
 * The success screen is reached for every status the POST returns, including ones that mean the
 * seller now has work to do: pay for the listing, confirm something on OLX, or accept that
 * moderation rejected it. Asking for a five-star rating on top of that is the single worst way to
 * spend a request, so only `new` (accepted, awaiting activation) and `active` (visible to buyers)
 * count. `Unknown` is excluded deliberately: it means OLX returned a status string this app does
 * not recognise, which is not a moment to celebrate blind.
 *
 * Exhaustive with no `else`, matching `AdvertStatus.state` - a status added to [AdvertStatus] stops
 * compiling until someone decides which side of this line it falls on.
 */
internal val AdvertStatus.isPublishCelebration: Boolean
    get() = when (this) {
        AdvertStatus.New,
        AdvertStatus.Active -> true

        AdvertStatus.Limited,
        AdvertStatus.Unpaid,
        AdvertStatus.Unconfirmed,
        AdvertStatus.Moderated,
        AdvertStatus.Blocked,
        AdvertStatus.Disabled,
        AdvertStatus.RemovedByModerator,
        AdvertStatus.RemovedByUser,
        AdvertStatus.Outdated,
        AdvertStatus.Unknown -> false
    }
