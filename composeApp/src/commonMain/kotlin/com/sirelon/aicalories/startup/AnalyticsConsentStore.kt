package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore

class AnalyticsConsentStore internal constructor(
    private val storage: KeyValueStore,
) {
    constructor() : this(createKeyValueStore("analytics_consent"))

    // No stored value means a fresh install (or a pre-consent-flow install): default to opted in.
    // An explicit AnalyticsConsent.Undecided written by resetConsent() (data erasure) is a real
    // stored value and is returned as-is below, not caught by this fallback.
    suspend fun read(): AnalyticsConsent =
        storage.getString(KEY_CONSENT)
            ?.let { savedValue -> AnalyticsConsent.entries.firstOrNull { it.name == savedValue } }
            ?: AnalyticsConsent.Granted

    suspend fun write(consent: AnalyticsConsent) {
        storage.putString(KEY_CONSENT, consent.name)
    }

    private companion object {
        const val KEY_CONSENT = "analytics_consent"
    }
}
