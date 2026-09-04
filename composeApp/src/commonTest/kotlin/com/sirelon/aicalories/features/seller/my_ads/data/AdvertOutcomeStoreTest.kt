package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Regression tests for [AdvertOutcomeStore] and [AdvertOutcomeRecord] (SIR-106's data source).
 * Together they collect suggested -> published -> achieved per advert, the only proprietary
 * dataset SellSnap can build on top of the AI's price guess.
 */
class AdvertOutcomeStoreTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `recordPublished then recordClosed merges into one record, keeping the publish-time suggestion`() = runBlocking {
        // This is the whole suggested -> published -> achieved chain the milestone exists to
        // collect, so the full chain must survive both writes landing on the same advert id.
        val store = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson)

        store.recordPublished(
            advertId = 42,
            suggestedPrice = 1000,
            minPrice = 900,
            maxPrice = 1100,
            publishedPrice = 1050,
            currency = "UAH",
        )
        store.recordClosed(advertId = 42, isSold = true, achievedPrice = 980)

        val outcome = store.outcomeFor(42)
        assertEquals(1000L, outcome?.suggestedPrice)
        assertEquals(900L, outcome?.minPrice)
        assertEquals(1100L, outcome?.maxPrice)
        assertEquals(1050L, outcome?.publishedPrice)
        assertEquals("UAH", outcome?.currency)
        assertEquals(true, outcome?.isSold)
        assertEquals(980L, outcome?.achievedPrice)
        assertEquals(true, outcome?.publishedAtEpochSeconds != null)
        assertEquals(true, outcome?.closedAtEpochSeconds != null)
    }

    @Test
    fun `recordClosed on an advert with no publish record still stores the outcome`() = runBlocking {
        // An advert published from the OLX app directly, or before this store shipped, has no
        // suggestion on record - the close-time half is still worth keeping on its own.
        val store = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson)

        store.recordClosed(advertId = 7, isSold = true, achievedPrice = 500)

        val outcome = store.outcomeFor(7)
        assertNull(outcome?.suggestedPrice)
        assertEquals(true, outcome?.isSold)
        assertEquals(500L, outcome?.achievedPrice)
    }

    @Test
    fun `achievedPrice is discarded when the advert did not sell`() = runBlocking {
        val store = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson)

        store.recordClosed(advertId = 7, isSold = false, achievedPrice = 500)

        assertNull(store.outcomeFor(7)?.achievedPrice)
    }

    @Test
    fun `daysLive counts whole days between publish and close`() {
        val record = AdvertOutcomeRecord(
            advertId = 1,
            publishedAtEpochSeconds = 0L,
            closedAtEpochSeconds = 3 * 86_400L + 12 * 3_600L, // 3.5 days later
        )

        assertEquals(3, record.daysLive)
    }

    @Test
    fun `daysLive is null when either the publish or close timestamp is missing`() {
        assertNull(AdvertOutcomeRecord(advertId = 1, publishedAtEpochSeconds = null, closedAtEpochSeconds = 100L).daysLive)
        assertNull(AdvertOutcomeRecord(advertId = 1, publishedAtEpochSeconds = 100L, closedAtEpochSeconds = null).daysLive)
    }

    @Test
    fun `priceDeltaPercent computes the percentage off the suggested price, negative when it sold for less`() {
        assertEquals(
            -2,
            AdvertOutcomeRecord(advertId = 1, suggestedPrice = 1000, achievedPrice = 980).priceDeltaPercent,
        )
        assertEquals(
            10,
            AdvertOutcomeRecord(advertId = 1, suggestedPrice = 1000, achievedPrice = 1100).priceDeltaPercent,
        )
    }

    @Test
    fun `priceDeltaPercent is null when either price is missing or the suggestion was 0, never a division by zero`() {
        assertNull(AdvertOutcomeRecord(advertId = 1, suggestedPrice = null, achievedPrice = 1100).priceDeltaPercent)
        assertNull(AdvertOutcomeRecord(advertId = 1, suggestedPrice = 1000, achievedPrice = null).priceDeltaPercent)
        assertNull(AdvertOutcomeRecord(advertId = 1, suggestedPrice = 0, achievedPrice = 500).priceDeltaPercent)
    }

    @Test
    fun `records for different advert ids do not clobber each other`() = runBlocking {
        val store = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson)

        store.recordPublished(advertId = 1, suggestedPrice = 100, minPrice = null, maxPrice = null, publishedPrice = 100, currency = "UAH")
        store.recordPublished(advertId = 2, suggestedPrice = 200, minPrice = null, maxPrice = null, publishedPrice = 200, currency = "PLN")

        assertEquals(100L, store.outcomeFor(1)?.suggestedPrice)
        assertEquals("UAH", store.outcomeFor(1)?.currency)
        assertEquals(200L, store.outcomeFor(2)?.suggestedPrice)
        assertEquals("PLN", store.outcomeFor(2)?.currency)
    }

    @Test
    fun `clearAll empties the store`() = runBlocking {
        val store = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson)
        store.recordPublished(advertId = 1, suggestedPrice = 100, minPrice = null, maxPrice = null, publishedPrice = 100, currency = "UAH")

        store.clearAll()

        assertNull(store.outcomeFor(1))
    }

    @Test
    fun `corrupt stored JSON reads back as empty instead of throwing`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore()
        storage.putString("outcomes", "{not valid json")
        val store = AdvertOutcomeStore(storage, testJson)

        assertNull(store.outcomeFor(advertId = 1))
    }

    @Test
    fun `a lifecycle action still succeeds after the store's own data is corrupted`() = runBlocking {
        // Outcome tracking is opportunistic data collection and must never break a lifecycle
        // action the seller asked for - a corrupt read must not stop a later write from landing.
        val storage = InMemoryOlxKeyValueStore()
        storage.putString("outcomes", "{not valid json")
        val store = AdvertOutcomeStore(storage, testJson)

        store.recordPublished(advertId = 1, suggestedPrice = 1000, minPrice = null, maxPrice = null, publishedPrice = 1000, currency = "UAH")

        assertEquals(1000L, store.outcomeFor(1)?.suggestedPrice)
    }
}
