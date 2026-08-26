package com.sirelon.sellsnap.features.auth.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountMigration
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxTokenStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OlxAccountMigrationTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `migrateIfNeeded copies legacy tokens into one active account keyed to stored country`() = runBlocking {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore()).apply { save(OlxCountry.PL) }
        val legacyTokens = sampleTokens()
        val legacyTokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply { write(legacyTokens) }
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        val migration = OlxAccountMigration(accountStore, legacyTokenStore, countryStore)

        migration.migrateIfNeeded()

        val record = accountStore.readRaw()!!
        assertEquals(1, record.accounts.size)
        val account = record.accounts.single()
        assertEquals(1, account.localIndex)
        assertEquals("pl", account.countryCode)
        assertEquals(legacyTokens, account.tokens)
        assertEquals(mapOf("pl" to 1), record.activeByCountry)
        assertEquals(2, record.nextLocalIndex)
        assertNull(legacyTokenStore.read())
    }

    @Test
    fun `migrateIfNeeded with no legacy blob produces empty accounts record`() = runBlocking {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore())
        val legacyTokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson)
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        val migration = OlxAccountMigration(accountStore, legacyTokenStore, countryStore)

        migration.migrateIfNeeded()

        val record = accountStore.readRaw()!!
        assertTrue(record.accounts.isEmpty())
        assertTrue(record.activeByCountry.isEmpty())
        assertEquals(1, record.nextLocalIndex)
    }

    @Test
    fun `migrateIfNeeded is idempotent`() = runBlocking {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore()).apply { save(OlxCountry.RO) }
        val legacyTokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply { write(sampleTokens()) }
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        val migration = OlxAccountMigration(accountStore, legacyTokenStore, countryStore)

        migration.migrateIfNeeded()
        migration.migrateIfNeeded()

        val record = accountStore.readRaw()!!
        assertEquals(1, record.accounts.size)
        assertEquals(1, record.accounts.single().localIndex)
    }

    @Test
    fun `migrateIfNeeded falls back to empty record on unreadable legacy blob`() = runBlocking {
        val legacyStorage = InMemoryOlxKeyValueStore().apply { putString("tokens", "{not valid json") }
        val legacyTokenStore = OlxTokenStore(legacyStorage, testJson)
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        val migration = OlxAccountMigration(accountStore, legacyTokenStore, OlxCountryStore(InMemoryOlxKeyValueStore()))

        migration.migrateIfNeeded()

        val record = accountStore.readRaw()!!
        assertTrue(record.accounts.isEmpty())
        assertTrue(record.activeByCountry.isEmpty())
    }

    private fun sampleTokens(): OlxTokens = OlxTokens(
        accessToken = "legacy-access-token",
        refreshToken = "legacy-refresh-token",
        expiresInSeconds = 86400,
        tokenType = "bearer",
        scope = "v2 read write",
        issuedAtEpochSeconds = 0,
    )
}
