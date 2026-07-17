package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry

// Package-level backing var so OlxConfig (an object) can read the current country
// without going through Koin DI. Initialized synchronously from device locale;
// overwritten by OlxCountryStore.loadFromStorage() at app startup.
@kotlin.concurrent.Volatile
internal var _currentOlxCountry: OlxCountry = OlxCountry.defaultForLocale()

class OlxCountryStore internal constructor(private val storage: KeyValueStore) {
    constructor() : this(createKeyValueStore("olx_country"))

    val current: OlxCountry get() = _currentOlxCountry

    suspend fun loadFromStorage() {
        _currentOlxCountry = OlxCountry.fromCode(storage.getString(KEY)) ?: _currentOlxCountry
    }

    suspend fun save(country: OlxCountry) {
        _currentOlxCountry = country
        storage.putString(KEY, country.code)
    }

    suspend fun clear() {
        storage.remove(KEY)
        _currentOlxCountry = OlxCountry.defaultForLocale()
    }

    companion object {
        private const val KEY = "country_code"
    }
}
