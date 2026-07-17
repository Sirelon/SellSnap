package com.sirelon.sellsnap.startup

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore

class AnalyticsConsentStore internal constructor(
    private val storage: KeyValueStore,
) {
    constructor() : this(createKeyValueStore("analytics_consent"))

    suspend fun read(): AnalyticsConsent =
        storage.getString(KEY_CONSENT)
            ?.let { savedValue -> AnalyticsConsent.entries.firstOrNull { it.name == savedValue } }
            ?: AnalyticsConsent.Undecided

    suspend fun write(consent: AnalyticsConsent) {
        storage.putString(KEY_CONSENT, consent.name)
    }

    private companion object {
        const val KEY_CONSENT = "analytics_consent"
    }
}
