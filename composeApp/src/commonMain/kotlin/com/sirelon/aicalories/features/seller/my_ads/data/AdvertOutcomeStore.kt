package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * What a listing was worth and what it actually went for, per advert id.
 *
 * The publish-time half is written when the advert goes up; the close-time half when the seller
 * answers OLX's "did it sell?" on deactivate. Together they complete
 * suggested -> published -> achieved, which is the only proprietary dataset SellSnap can build:
 * the AI guesses a price from a photo today with no feedback on whether the guess was any good.
 *
 * Recording it costs almost nothing now and cannot be reconstructed retroactively. Nothing here
 * is sent to OLX beyond the `is_success` flag the deactivate command itself requires, and nothing
 * here leaves the device except as the bucketed analytics in SIR-106.
 */
@Serializable
internal class AdvertOutcomeRecord(
    @SerialName("advert_id")
    val advertId: Long,

    /** The AI's suggested price at generation time, in whole currency units. */
    @SerialName("suggested_price")
    val suggestedPrice: Long? = null,

    @SerialName("min_price")
    val minPrice: Long? = null,

    @SerialName("max_price")
    val maxPrice: Long? = null,

    /** What the seller actually listed it for, which may differ from the suggestion. */
    @SerialName("published_price")
    val publishedPrice: Long? = null,

    @SerialName("currency")
    val currency: String = "",

    @SerialName("published_at_epoch_seconds")
    val publishedAtEpochSeconds: Long? = null,

    /** Null until the listing is closed. True = sold, false = closed unsold. */
    @SerialName("is_sold")
    val isSold: Boolean? = null,

    /** Only ever set for a sale, and only when the seller chose to enter it - the field is skippable. */
    @SerialName("achieved_price")
    val achievedPrice: Long? = null,

    @SerialName("closed_at_epoch_seconds")
    val closedAtEpochSeconds: Long? = null,
) {
    /** Whole days the listing was live, when both ends are known. Drives SIR-106's `days_live`. */
    val daysLive: Int?
        get() {
            val from = publishedAtEpochSeconds ?: return null
            val to = closedAtEpochSeconds ?: return null
            return ((to - from) / SECONDS_PER_DAY).toInt().coerceAtLeast(0)
        }

    /**
     * How far the achieved price landed from the AI's suggestion, as a percentage of the
     * suggestion. Negative means it sold for less than suggested. Null unless both are known -
     * the seller can skip the price, and adverts published before this store existed have no
     * suggestion at all.
     */
    val priceDeltaPercent: Int?
        get() {
            val suggested = suggestedPrice?.takeIf { it > 0 } ?: return null
            val achieved = achievedPrice ?: return null
            return (((achieved - suggested) * 100) / suggested).toInt()
        }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}

@Serializable
internal class AdvertOutcomesRecord(
    @SerialName("outcomes")
    val outcomes: List<AdvertOutcomeRecord> = emptyList(),
)

/**
 * Single JSON blob under key "outcomes" in the "advert_outcomes" [KeyValueStore], same shape as
 * [com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore]. Every mutation is a
 * read-modify-write under [mutex]: publish-time and close-time writes for different adverts can
 * overlap, and a clobbered record cannot be recovered.
 *
 * Failures are swallowed. This is opportunistic data collection and must never break a lifecycle
 * action the seller asked for.
 */
internal class AdvertOutcomeStore internal constructor(
    private val storage: KeyValueStore,
    private val json: Json,
) {
    constructor(json: Json) : this(createKeyValueStore("advert_outcomes"), json)

    private val mutex = Mutex()

    /** Called on publish success, so the AI's guess is on record before any outcome exists. */
    suspend fun recordPublished(
        advertId: Long,
        suggestedPrice: Long?,
        minPrice: Long?,
        maxPrice: Long?,
        publishedPrice: Long?,
        currency: String,
    ) {
        mutate(advertId) { existing ->
            AdvertOutcomeRecord(
                advertId = advertId,
                suggestedPrice = suggestedPrice,
                minPrice = minPrice,
                maxPrice = maxPrice,
                publishedPrice = publishedPrice,
                currency = currency,
                publishedAtEpochSeconds = nowEpochSeconds(),
                isSold = existing?.isSold,
                achievedPrice = existing?.achievedPrice,
                closedAtEpochSeconds = existing?.closedAtEpochSeconds,
            )
        }
    }

    /**
     * Called when the seller answers OLX's "did it sell?". Keeps whatever publish-time data
     * exists - an advert published before this store shipped, or from the OLX app directly, has
     * none, and the outcome is still worth keeping on its own.
     */
    suspend fun recordClosed(advertId: Long, isSold: Boolean, achievedPrice: Long?) {
        mutate(advertId) { existing ->
            AdvertOutcomeRecord(
                advertId = advertId,
                suggestedPrice = existing?.suggestedPrice,
                minPrice = existing?.minPrice,
                maxPrice = existing?.maxPrice,
                publishedPrice = existing?.publishedPrice,
                currency = existing?.currency.orEmpty(),
                publishedAtEpochSeconds = existing?.publishedAtEpochSeconds,
                isSold = isSold,
                achievedPrice = achievedPrice.takeIf { isSold },
                closedAtEpochSeconds = nowEpochSeconds(),
            )
        }
    }

    /**
     * Drops the close-time half, keeping the publish-time half. Called when a listing is put back
     * up: it is live again, so it has no outcome, and the next sale it makes has to be able to
     * record - and report - itself as a first sale.
     */
    suspend fun clearOutcome(advertId: Long) {
        mutate(advertId) { existing ->
            AdvertOutcomeRecord(
                advertId = advertId,
                suggestedPrice = existing?.suggestedPrice,
                minPrice = existing?.minPrice,
                maxPrice = existing?.maxPrice,
                publishedPrice = existing?.publishedPrice,
                currency = existing?.currency.orEmpty(),
                publishedAtEpochSeconds = existing?.publishedAtEpochSeconds,
                isSold = null,
                achievedPrice = null,
                closedAtEpochSeconds = null,
            )
        }
    }

    suspend fun outcomeFor(advertId: Long): AdvertOutcomeRecord? = mutex.withLock {
        readLocked().outcomes.find { it.advertId == advertId }
    }

    /** Part of "Delete my SellSnap data", alongside the other locally held seller data. */
    suspend fun clearAll() {
        mutex.withLock { runCatching { storage.remove(KEY) } }
    }

    private suspend fun mutate(advertId: Long, transform: (AdvertOutcomeRecord?) -> AdvertOutcomeRecord) {
        mutex.withLock {
            runCatching {
                val current = readLocked()
                val updated = transform(current.outcomes.find { it.advertId == advertId })
                val outcomes = current.outcomes.filter { it.advertId != advertId } + updated
                storage.putString(KEY, json.encodeToString(AdvertOutcomesRecord(outcomes)))
            }
        }
    }

    private suspend fun readLocked(): AdvertOutcomesRecord {
        val raw = runCatching { storage.getString(KEY) }.getOrNull() ?: return AdvertOutcomesRecord()
        return runCatching { json.decodeFromString<AdvertOutcomesRecord>(raw) }.getOrNull()
            ?: AdvertOutcomesRecord()
    }

    private fun nowEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

    private companion object {
        const val KEY = "outcomes"
    }
}
