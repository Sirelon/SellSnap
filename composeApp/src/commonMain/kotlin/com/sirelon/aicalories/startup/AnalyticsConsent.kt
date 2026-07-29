package com.sirelon.sellsnap.startup

/**
 * The user's decision about analytics + crash-reporting collection.
 *
 * [Undecided] is the initial state on a fresh install (and for installs that predate the consent
 * flow); it triggers the one-time consent prompt. Collection stays off unless [Granted].
 */
enum class AnalyticsConsent {
    Undecided,
    Granted,
    Denied,
}
