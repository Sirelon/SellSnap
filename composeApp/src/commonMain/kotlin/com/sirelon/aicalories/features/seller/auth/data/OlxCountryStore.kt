package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _currentFlow = MutableStateFlow(_currentOlxCountry)

    /**
     * [current] as a flow. `AppKey.Profile(reason)` is pushed on top of the generate/preview flow
     * with the GenerateAd ViewModel alive underneath, and a guest can change country there
     * (`ProfileEvent.CountrySelected`) and come back; `loadFromStorage()` also runs
     * asynchronously during startup. A one-shot read of [current] would leave anything filtered
     * by country showing the stale one in both cases.
     */
    val currentFlow: StateFlow<OlxCountry> = _currentFlow.asStateFlow()

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
        _currentFlow.value = country
        analytics.setUserProperty("olx_country", country.code)
    }

    companion object {
        private const val KEY = "country_code"
    }
}
