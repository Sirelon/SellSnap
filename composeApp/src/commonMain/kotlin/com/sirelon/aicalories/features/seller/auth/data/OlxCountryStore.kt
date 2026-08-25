package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry

// Package-level backing var so OlxConfig (an object) can read the current country
// without going through Koin DI. Initialized synchronously from device locale;
// overwritten by OlxCountryStore.loadFromStorage() at app startup.
@kotlin.concurrent.Volatile
internal var _currentOlxCountry: OlxCountry = OlxCountry.defaultForLocale()

class OlxCountryStore internal constructor(
    private val storage: KeyValueStore,
    private val analytics: Analytics,
) {
    constructor(analytics: Analytics) : this(createKeyValueStore("olx_country"), analytics)

    val current: OlxCountry get() = _currentOlxCountry

    suspend fun loadFromStorage() {
        applyCountry(OlxCountry.fromCode(storage.getString(KEY)) ?: _currentOlxCountry)
    }

    suspend fun save(country: OlxCountry) {
        applyCountry(country)
        storage.putString(KEY, country.code)
    }

    suspend fun clear() {
        storage.remove(KEY)
        applyCountry(OlxCountry.defaultForLocale())
    }

    // Segments every subsequent analytics event by market, so the funnel can be sliced per OLX country.
    private fun applyCountry(country: OlxCountry) {
        _currentOlxCountry = country
        analytics.setUserProperty("olx_country", country.code)
    }

    companion object {
        private const val KEY = "country_code"
    }
}
