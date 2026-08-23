package com.sirelon.sellsnap.features.auth.data

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxProfileSnapshot
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OlxAccountStoreTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `localIndex is never reused after disconnect`() = runBlocking {
        val store = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)

        val first = store.addOrUpdateAccount(
            countryCode = "ua",
            olxUserId = 1L,
            tokens = sampleTokens("t1"),
            profile = null,
            makeActive = true,
        )
        val second = store.addOrUpdateAccount(
            countryCode = "ua",
            olxUserId = 2L,
            tokens = sampleTokens("t2"),
            profile = null,
            makeActive = false,
        )
        assertEquals(1, first.account.localIndex)
        assertEquals(2, second.account.localIndex)

        store.disconnect(first.account.localIndex)

        val third = store.addOrUpdateAccount(
            countryCode = "ua",
            olxUserId = 3L,
            tokens = sampleTokens("t3"),
            profile = null,
            makeActive = false,
        )
        assertEquals(3, third.account.localIndex)
    }

    @Test
    fun `addOrUpdateAccount with same olxUserId updates existing account and reports duplicate`() = runBlocking {
        val store = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)

        val first = store.addOrUpdateAccount(
            countryCode = "ua",
            olxUserId = 42L,
            tokens = sampleTokens("t1"),
            profile = null,
            makeActive = true,
        )
        assertFalse(first.wasDuplicate)

        val second = store.addOrUpdateAccount(
            countryCode = "ua",
            olxUserId = 42L,
            tokens = sampleTokens("t2"),
            profile = null,
            makeActive = true,
        )
        assertTrue(second.wasDuplicate)
        assertEquals(first.account.localIndex, second.account.localIndex)

        val record = store.readRaw()!!
        assertEquals(1, record.accounts.size)
        assertEquals("t2", record.accounts.single().tokens.accessToken)
    }

    @Test
    fun `backfillIdentityIfMissing sets olxUserId and profile only when currently null`() = runBlocking {
        val store = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        store.write(
            OlxAccountsRecord(
                accounts = listOf(
                    // The migrated pre-SIR-83 account: no olxUserId, no profile, ever.
                    OlxAccountRecord(
                        localIndex = 1,
                        countryCode = "ua",
                        olxUserId = null,
                        tokens = sampleTokens("legacy"),
                        lastUsedAtEpochSeconds = 0,
                        lastRefreshedAtEpochSeconds = 0,
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )

        val profile = OlxProfileSnapshot(name = "Oleg", email = "oleg@example.com", avatarUrl = null, isBusiness = false)
        store.backfillIdentityIfMissing(localIndex = 1, olxUserId = 555L, profile = profile)

        val backfilled = store.readRaw()!!.accounts.single()
        assertEquals(555L, backfilled.olxUserId)
        assertEquals("oleg@example.com", backfilled.profile?.email)

        // A second backfill attempt with a different id must never clobber a real id.
        store.backfillIdentityIfMissing(localIndex = 1, olxUserId = 999L, profile = profile)
        assertEquals(555L, store.readRaw()!!.accounts.single().olxUserId)
    }

    @Test
    fun `addOrUpdateAccount matches a not-yet-backfilled account by email so it never duplicates`() = runBlocking {
        val store = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        val profile = OlxProfileSnapshot(name = "Oleg", email = "oleg@example.com", avatarUrl = null, isBusiness = false)
        store.write(
            OlxAccountsRecord(
                accounts = listOf(
                    // Has a profile (e.g. from a prior partial fetch) but no id yet - the exact
                    // window a broken Android force-relogin can land in before startup backfills it.
                    OlxAccountRecord(
                        localIndex = 1,
                        countryCode = "ua",
                        olxUserId = null,
                        tokens = sampleTokens("legacy"),
                        profile = profile,
                        lastUsedAtEpochSeconds = 0,
                        lastRefreshedAtEpochSeconds = 0,
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )

        val result = store.addOrUpdateAccount(
            countryCode = "ua",
            olxUserId = 555L,
            tokens = sampleTokens("new-token"),
            profile = profile,
            makeActive = true,
        )

        assertTrue(result.wasDuplicate)
        assertEquals(1, result.account.localIndex)
        val record = store.readRaw()!!
        assertEquals(1, record.accounts.size)
        assertEquals(555L, record.accounts.single().olxUserId)
    }

    @Test
    fun `updateTokens leaves lastUsedAtEpochSeconds untouched when updateLastUsed is false`() = runBlocking {
        // A background keep-alive refresh is not seller activity - it must not look like use,
        // or it caps account_token_expired_unused's days_since_last_use at the keep-alive
        // staleness threshold and skews disconnect's most-recently-used promotion.
        val store = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        store.write(
            OlxAccountsRecord(
                accounts = listOf(
                    OlxAccountRecord(
                        localIndex = 1,
                        countryCode = "ua",
                        tokens = sampleTokens("old"),
                        lastUsedAtEpochSeconds = 100,
                        lastRefreshedAtEpochSeconds = 100,
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )

        store.updateTokens(1, sampleTokens("keep-alive-refreshed"), lastRefreshedAtEpochSeconds = 999, updateLastUsed = false)

        val afterKeepAlive = store.readRaw()!!.accounts.single()
        assertEquals(999, afterKeepAlive.lastRefreshedAtEpochSeconds)
        assertEquals(100, afterKeepAlive.lastUsedAtEpochSeconds)

        // The reactive 401-refresh path (real seller activity) keeps the default and does update it.
        store.updateTokens(1, sampleTokens("reactive-refreshed"), lastRefreshedAtEpochSeconds = 1500)
        val afterReactive = store.readRaw()!!.accounts.single()
        assertEquals(1500, afterReactive.lastUsedAtEpochSeconds)
    }

    @Test
    fun `concurrent updateTokens on different accounts both persist`() = runBlocking {
        val storage = DelayingKeyValueStore(InMemoryOlxKeyValueStore())
        val store = OlxAccountStore(storage, testJson)
        store.write(
            OlxAccountsRecord(
                accounts = listOf(
                    OlxAccountRecord(
                        localIndex = 1,
                        countryCode = "ua",
                        tokens = sampleTokens("initial-1"),
                        lastUsedAtEpochSeconds = 0,
                        lastRefreshedAtEpochSeconds = 0,
                    ),
                    OlxAccountRecord(
                        localIndex = 2,
                        countryCode = "ua",
                        tokens = sampleTokens("initial-2"),
                        lastUsedAtEpochSeconds = 0,
                        lastRefreshedAtEpochSeconds = 0,
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 3,
            ),
        )

        coroutineScope {
            launch { store.updateTokens(1, sampleTokens("fresh-1"), 111L) }
            launch { store.updateTokens(2, sampleTokens("fresh-2"), 222L) }
        }

        val record = store.readRaw()!!
        assertEquals("fresh-1", record.accounts.find { it.localIndex == 1 }?.tokens?.accessToken)
        assertEquals("fresh-2", record.accounts.find { it.localIndex == 2 }?.tokens?.accessToken)
    }

    @Test
    fun `disconnect promotes most recently used remaining account and clears pointer when none remain`() = runBlocking {
        val store = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        store.write(
            OlxAccountsRecord(
                accounts = listOf(
                    OlxAccountRecord(
                        localIndex = 1,
                        countryCode = "ua",
                        tokens = sampleTokens("t1"),
                        lastUsedAtEpochSeconds = 100,
                        lastRefreshedAtEpochSeconds = 100,
                    ),
                    OlxAccountRecord(
                        localIndex = 2,
                        countryCode = "ua",
                        tokens = sampleTokens("t2"),
                        lastUsedAtEpochSeconds = 300,
                        lastRefreshedAtEpochSeconds = 300,
                    ),
                    OlxAccountRecord(
                        localIndex = 3,
                        countryCode = "ua",
                        tokens = sampleTokens("t3"),
                        lastUsedAtEpochSeconds = 200,
                        lastRefreshedAtEpochSeconds = 200,
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 4,
            ),
        )

        store.disconnect(1)
        val afterFirstDisconnect = store.readRaw()!!
        assertEquals(2, afterFirstDisconnect.activeByCountry["ua"])

        store.disconnect(2)
        store.disconnect(3)
        val afterAllDisconnected = store.readRaw()!!
        assertTrue(afterAllDisconnected.accounts.none { it.countryCode == "ua" })
        assertNull(afterAllDisconnected.activeByCountry["ua"])
        assertFalse(afterAllDisconnected.activeByCountry.containsKey("ua"))
    }

    @Test
    fun `recordAuthFailure and clearAuthFailures track counters for reconnect and first-connect cases`() = runBlocking {
        val store = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        store.write(
            OlxAccountsRecord(
                accounts = listOf(
                    OlxAccountRecord(
                        localIndex = 1,
                        countryCode = "ua",
                        tokens = sampleTokens(),
                        lastUsedAtEpochSeconds = 0,
                        lastRefreshedAtEpochSeconds = 0,
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )

        // Reconnect case: localIndex is set, an account already exists.
        store.recordAuthFailure(localIndex = 1, countryCode = "ua")
        store.recordAuthFailure(localIndex = 1, countryCode = "ua")
        assertEquals(2, store.consecutiveFailureCount(localIndex = 1, countryCode = "ua"))

        val lastFailureAt = store.readRaw()!!.accounts.single().lastAuthFailureAtEpochSeconds!!
        assertEquals(60L, store.remainingCooldownSeconds(localIndex = 1, countryCode = "ua", nowEpochSeconds = lastFailureAt))
        assertEquals(0L, store.remainingCooldownSeconds(localIndex = 1, countryCode = "ua", nowEpochSeconds = lastFailureAt + 60))

        store.clearAuthFailures(localIndex = 1, countryCode = "ua")
        assertEquals(0, store.consecutiveFailureCount(localIndex = 1, countryCode = "ua"))
        assertNull(store.readRaw()!!.accounts.single().lastAuthFailureAtEpochSeconds)

        // First-connect/add case: no account exists yet, keyed by country only.
        assertEquals(0, store.consecutiveFailureCount(localIndex = null, countryCode = "pl"))
        store.recordAuthFailure(localIndex = null, countryCode = "pl")
        assertEquals(1, store.consecutiveFailureCount(localIndex = null, countryCode = "pl"))

        store.clearAuthFailures(localIndex = null, countryCode = "pl")
        assertEquals(0, store.consecutiveFailureCount(localIndex = null, countryCode = "pl"))
    }

    private fun sampleTokens(accessToken: String = "access-token"): OlxTokens = OlxTokens(
        accessToken = accessToken,
        refreshToken = "refresh-token",
        expiresInSeconds = 86400,
        tokenType = "bearer",
        scope = "v2 read write",
        issuedAtEpochSeconds = 0,
    )

    /** Wraps a [KeyValueStore] with an artificial suspension point to force real coroutine interleaving. */
    private class DelayingKeyValueStore(private val delegate: KeyValueStore) : KeyValueStore {
        override suspend fun getString(key: String): String? {
            delay(5)
            return delegate.getString(key)
        }

        override suspend fun putString(key: String, value: String) {
            delay(5)
            delegate.putString(key, value)
        }

        override suspend fun remove(key: String) {
            delay(5)
            delegate.remove(key)
        }
    }
}
