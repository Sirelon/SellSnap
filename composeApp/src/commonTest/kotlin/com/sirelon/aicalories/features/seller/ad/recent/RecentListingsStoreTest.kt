package com.sirelon.sellsnap.features.seller.ad.recent

import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * Regression tests for [RecentListingsStore] (SIR-133). Records carry a random id and a timestamp,
 * so every assertion reads the listing or its title, never a whole [RecentListing].
 */
class RecentListingsStoreTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun listing(title: String) = AdvertisementWithAttributes(
        advertisement = Advertisement(
            title = title,
            description = "Description of $title",
            images = listOf("https://x/$title.jpg"),
            suggestedPrice = 100f,
            minPrice = 90f,
            maxPrice = 110f,
        ),
        filledAttributes = emptyMap(),
        sellerPrompt = "worn twice",
        // The preview ViewModel is keyed on this id (App.kt), so it has to survive the round trip.
        generationSessionId = "session-$title",
        lastAttemptId = "attempt-$title",
    )

    @Test
    fun `add puts the newest listing first`() = runBlocking {
        val store = RecentListingsStore(InMemoryOlxKeyValueStore(), testJson)
        val first = listing("A")
        val second = listing("B")

        store.add(first, countryCode = "ua")
        store.add(second, countryCode = "ua")

        assertEquals(listOf(second, first), store.listings.first().map { it.listing })
    }

    @Test
    fun `the store keeps at most MAX_RECENT_LISTINGS, dropping the oldest`() = runBlocking {
        val store = RecentListingsStore(InMemoryOlxKeyValueStore(), testJson)

        repeat(MAX_RECENT_LISTINGS + 1) { index ->
            store.add(listing("listing-$index"), countryCode = "ua")
        }

        val titles = store.listings.first().map { it.listing.advertisement.title }
        assertEquals(MAX_RECENT_LISTINGS, titles.size)
        assertTrue("listing-0" !in titles, "the first-added listing is the one dropped")
        assertEquals("listing-$MAX_RECENT_LISTINGS", titles.first())
    }

    @Test
    fun `listings survive a new store instance over the same storage`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore()
        val saved = listing("A")
        RecentListingsStore(storage, testJson).add(saved, countryCode = "pl")

        val reopened = RecentListingsStore(storage, testJson).listings.first()

        assertEquals(listOf(saved), reopened.map { it.listing })
        assertEquals("pl", reopened.single().countryCode)
    }

    @Test
    fun `undecodable storage reads as empty`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore()
        storage.putString("listings", "{not json")

        val store = RecentListingsStore(storage, testJson)

        assertTrue(store.listings.first().isEmpty())
    }

    @Test
    fun `clearAll empties the store and the flow`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore()
        val store = RecentListingsStore(storage, testJson)
        store.add(listing("A"), countryCode = "ua")

        store.clearAll()

        assertTrue(store.listings.first().isEmpty())
        assertTrue(RecentListingsStore(storage, testJson).listings.first().isEmpty())
    }
}
