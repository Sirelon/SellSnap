package com.sirelon.sellsnap.features.seller.ad.recent

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * One generation the seller can come back to. [listing] is the generated listing exactly as the
 * model produced it; preview edits are not written back.
 *
 * This is also the on-disk shape. A new field without a default on [AdvertisementWithAttributes]
 * or [com.sirelon.sellsnap.features.seller.ad.Advertisement] makes every stored record
 * undecodable, and the store then reads as empty and the next generation overwrites the history.
 */
@Immutable
@Serializable
data class RecentListing(
    @SerialName("id")
    val id: String,

    @SerialName("created_at_epoch_seconds")
    val createdAtEpochSeconds: Long,

    /** The OLX country the listing was generated for, so the list can follow the current market. */
    @SerialName("country_code")
    val countryCode: String,

    @SerialName("listing")
    val listing: AdvertisementWithAttributes,
)

@Serializable
internal class RecentListingsRecord(
    @SerialName("listings")
    val listings: List<RecentListing> = emptyList(),
)

internal const val MAX_RECENT_LISTINGS = 10

/**
 * The last [MAX_RECENT_LISTINGS] generated listings, newest first, guests included.
 *
 * Sellers regenerate the same item just to copy its text again, and every repeat is a ~20 second
 * wait and a model call. Keeping the result on the device lets them copy it from the generate
 * screen instead.
 *
 * Single JSON blob under key "listings" in the "recent_listings" [KeyValueStore], same shape as
 * [com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore]. A listing is written once,
 * when its generation succeeds; preview edits, regeneration and publishing never update it. Every
 * mutation is a read-modify-write under [mutex].
 *
 * Failures are swallowed: this is a convenience copy and must never break the generate flow.
 *
 * Public with an internal primary constructor: `GenerateAdViewModel`'s constructor is public, so
 * its parameter types cannot be internal.
 */
class RecentListingsStore internal constructor(
    private val storage: KeyValueStore,
    private val json: Json,
) {
    constructor(json: Json) : this(createKeyValueStore("recent_listings"), json)

    private val mutex = Mutex()

    // null = not read from disk yet
    private val loaded = MutableStateFlow<List<RecentListing>?>(null)

    /** Newest first. Reads disk once, on first collection; every [add] and [clearAll] updates it. */
    val listings: Flow<List<RecentListing>> = loaded.onStart { ensureLoaded() }.filterNotNull()

    suspend fun add(listing: AdvertisementWithAttributes, countryCode: String) {
        mutex.withLock {
            runCatching {
                val entry = RecentListing(
                    id = Uuid.random().toString(),
                    createdAtEpochSeconds = Clock.System.now().toEpochMilliseconds() / 1000,
                    countryCode = countryCode,
                    listing = listing,
                )
                val updated = (listOf(entry) + (loaded.value ?: readLocked())).take(MAX_RECENT_LISTINGS)
                storage.putString(KEY, json.encodeToString(RecentListingsRecord(updated)))
                loaded.value = updated
            }
        }
    }

    /** Part of "Delete my SellSnap data", alongside the other locally held seller data. */
    suspend fun clearAll() {
        mutex.withLock {
            runCatching { storage.remove(KEY) }
            loaded.value = emptyList()
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded.value != null) return
        mutex.withLock {
            if (loaded.value == null) loaded.value = readLocked()
        }
    }

    private suspend fun readLocked(): List<RecentListing> {
        val raw = runCatching { storage.getString(KEY) }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<RecentListingsRecord>(raw).listings }.getOrNull()
            ?: emptyList()
    }

    private companion object {
        const val KEY = "listings"
    }
}
