package com.sirelon.sellsnap.features.seller.drafts.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sirelon.sellsnap.db.SellSnapDatabase
import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import com.sirelon.sellsnap.features.seller.drafts.Draft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightDraftsRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }
    private val database = SellSnapDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), SellSnapDatabase.Schema),
    )
    private val repository = SqlDelightDraftsRepository(database = database, json = json)

    @Test
    fun `drafts come back most recently updated first`() = runTest {
        repository.upsert(draft(id = "older", updatedAt = 100))
        repository.upsert(draft(id = "newest", updatedAt = 300))
        repository.upsert(draft(id = "middle", updatedAt = 200))

        assertEquals(listOf("newest", "middle", "older"), repository.drafts().first().map { it.id })
    }

    @Test
    fun `get returns every field of the stored draft`() = runTest {
        val stored = draft(
            id = "session-1",
            countryCode = "ua",
            createdAt = 10,
            updatedAt = 20,
            listing = AdvertisementWithAttributes(
                advertisement = Advertisement(
                    title = "Куртка з ґудзиками",
                    description = "Майже нова, носили лише один сезон",
                    images = listOf("https://example.com/1.jpg", "https://example.com/2.jpg"),
                    suggestedPrice = 1500f,
                    minPrice = 1200f,
                    maxPrice = 1800f,
                ),
                filledAttributes = mapOf("size" to listOf(OlxAttributeValue(code = "m", label = "M"))),
                sellerPrompt = "Ґудзики замінено, є чек",
                generationSessionId = "session-1",
                lastAttemptId = "attempt-3",
            ),
        )

        repository.upsert(stored)

        assertEquals(stored, repository.get("session-1"))
    }

    @Test
    fun `get is null for an id that was never stored`() = runTest {
        assertNull(repository.get("missing"))
    }

    @Test
    fun `upsert with the same id replaces the draft`() = runTest {
        repository.upsert(draft(id = "same", updatedAt = 100, title = "Before"))
        repository.upsert(draft(id = "same", updatedAt = 200, title = "After"))

        val drafts = repository.drafts().first()

        assertEquals(1, drafts.size)
        assertEquals("After", drafts.single().listing.advertisement.title)
        assertEquals(200, drafts.single().updatedAtEpochSeconds)
    }

    @Test
    fun `delete removes only that draft`() = runTest {
        repository.upsert(draft(id = "keep", updatedAt = 100))
        repository.upsert(draft(id = "drop", updatedAt = 200))

        repository.delete("drop")

        assertEquals(listOf("keep"), repository.drafts().first().map { it.id })
        assertNull(repository.get("drop"))
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        repository.upsert(draft(id = "a", updatedAt = 100))
        repository.upsert(draft(id = "b", updatedAt = 200))

        repository.deleteAll()

        assertEquals(emptyList(), repository.drafts().first())
    }

    @Test
    fun `a row whose json no longer decodes is skipped`() = runTest {
        repository.upsert(draft(id = "good", updatedAt = 100))
        database.draftQueries.upsert(
            id = "broken",
            country_code = "ua",
            created_at_epoch_seconds = 200,
            updated_at_epoch_seconds = 200,
            listing_json = "{not json",
        )

        assertEquals(listOf("good"), repository.drafts().first().map { it.id })
        assertNull(repository.get("broken"))
    }

    private fun draft(
        id: String,
        updatedAt: Long,
        countryCode: String = "ua",
        createdAt: Long = updatedAt,
        title: String = "Title $id",
        listing: AdvertisementWithAttributes = AdvertisementWithAttributes(
            advertisement = Advertisement(
                title = title,
                description = "Description $id",
                images = emptyList(),
                suggestedPrice = 100f,
                minPrice = 50f,
                maxPrice = 150f,
            ),
            filledAttributes = emptyMap(),
            generationSessionId = id,
        ),
    ) = Draft(
        id = id,
        countryCode = countryCode,
        createdAtEpochSeconds = createdAt,
        updatedAtEpochSeconds = updatedAt,
        listing = listing,
    )
}
