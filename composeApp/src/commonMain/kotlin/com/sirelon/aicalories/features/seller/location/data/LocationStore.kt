package com.sirelon.sellsnap.features.seller.location.data

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import kotlinx.serialization.json.Json

/**
 * The seller's city, one per OLX market: an OLX city id means nothing in another country, so a
 * Kyiv picked on olx.ua must not follow the seller to olx.pt.
 */
class LocationStore internal constructor(
    private val storage: KeyValueStore,
    private val json: Json,
) {
    constructor(json: Json) : this(createKeyValueStore("olx_location"), json)

    /**
     * The location saved for [countryCode]. A location saved before it was kept per country is
     * moved to the first country that asks for it, which is the country selected at upgrade time.
     */
    suspend fun read(countryCode: String): OlxLocation? {
        val raw = storage.getString(keyFor(countryCode))
            ?: storage.getString(LEGACY_KEY)?.also {
                storage.putString(keyFor(countryCode), it)
                storage.remove(LEGACY_KEY)
            }
        return raw?.let { json.decodeFromString<OlxLocation>(it) }
    }

    suspend fun write(countryCode: String, location: OlxLocation) {
        storage.putString(keyFor(countryCode), json.encodeToString(location))
    }

    suspend fun clear() {
        storage.remove(LEGACY_KEY)
        OlxCountry.all.forEach { storage.remove(keyFor(it.code)) }
    }

    private fun keyFor(countryCode: String) = "selected_location_$countryCode"

    private companion object {
        const val LEGACY_KEY = "selected_location"
    }
}
