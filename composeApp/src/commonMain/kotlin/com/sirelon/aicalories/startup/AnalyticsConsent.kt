package com.sirelon.sellsnap.startup

/**
 * The user's decision about analytics + crash-reporting collection.
 *
 * A fresh install defaults to [Granted] (see [AnalyticsConsentStore.read]) - collection starts
 * immediately, and the user can opt out later from Settings. [Undecided] only occurs after a data
 * erasure resets consent ([AnalyticsConsentRepository.resetConsent]), where it re-triggers the
 * one-time consent prompt and keeps collection off until the user decides again.
 */
enum class AnalyticsConsent {
    Undecided,
    Granted,
    Denied,
}
