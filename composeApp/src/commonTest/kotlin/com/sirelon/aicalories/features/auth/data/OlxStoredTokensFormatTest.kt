package com.sirelon.sellsnap.features.auth.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxTokenStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Pins the JSON that the app writes to disk for OLX tokens. Installed apps hold these exact
 * strings, so a change here logs every seller out after an update (SIR-179). The blobs below
 * were captured from the release before the stored format was split from [OlxTokens].
 */
class OlxStoredTokensFormatTest {

    // Same Json the app builds in `networkModule`.
    private val appJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val tokens = OlxTokens(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        expiresInSeconds = 86400,
        tokenType = "bearer",
        scope = "v2 read write",
        issuedAtEpochSeconds = 1_700_000_000,
    )

    @Test
    fun `legacy token blob from the current release decodes`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore().apply { putString("tokens", LEGACY_TOKENS_JSON) }

        assertEquals(tokens, OlxTokenStore(storage, appJson).read())
    }

    @Test
    fun `legacy token blob is written byte for byte as before`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore()

        OlxTokenStore(storage, appJson).write(tokens)

        assertEquals(LEGACY_TOKENS_JSON, storage.getString("tokens"))
    }

    @Test
    fun `account blob from the current release decodes`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore().apply { putString("accounts", ACCOUNTS_JSON) }

        val account = OlxAccountStore(storage, appJson).readRaw()!!.accounts.single()

        assertEquals(tokens, account.tokens)
        assertEquals(7L, account.olxUserId)
    }

    @Test
    fun `account blob without a refresh token decodes`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore().apply {
            putString("accounts", ACCOUNTS_JSON.replace(""","refresh_token":"refresh-1"""", ""))
        }

        val account = OlxAccountStore(storage, appJson).readRaw()!!.accounts.single()

        assertEquals(tokens.copy(refreshToken = null), account.tokens)
    }

    @Test
    fun `account blob is written with the same token keys`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore().apply { putString("accounts", ACCOUNTS_JSON) }
        val store = OlxAccountStore(storage, appJson)

        store.write(store.readRaw()!!)

        val written = appJson.parseToJsonElement(storage.getString("accounts")!!)
        val writtenTokens = written.jsonObject["accounts"]!!.jsonArray.single().jsonObject["tokens"]!!.jsonObject
        assertEquals(
            appJson.parseToJsonElement(LEGACY_TOKENS_JSON).jsonObject,
            writtenTokens,
        )
    }

    @Test
    fun `account round trip keeps every token field`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore()
        val store = OlxAccountStore(storage, appJson)
        store.addOrUpdateAccount(
            countryCode = "pl",
            olxUserId = 7L,
            tokens = tokens,
            profile = null,
            makeActive = true,
        )

        val reopened = OlxAccountStore(storage, appJson).readRaw()!!.accounts.single()

        assertEquals(tokens, reopened.tokens)
    }

    @Test
    fun `token without refresh token omits the key`() = runBlocking {
        val storage = InMemoryOlxKeyValueStore()

        OlxTokenStore(storage, appJson).write(tokens.copy(refreshToken = null))

        val written: JsonObject = appJson.parseToJsonElement(storage.getString("tokens")!!).jsonObject
        assertFalse("refresh_token" in written)
        assertEquals("access-1", written["access_token"]!!.jsonPrimitive.content)
    }

    private companion object {
        const val LEGACY_TOKENS_JSON =
            """{"access_token":"access-1","refresh_token":"refresh-1","expires_in":86400,"token_type":"bearer","scope":"v2 read write","issued_at_epoch_seconds":1700000000}"""

        const val ACCOUNTS_JSON =
            """{"accounts":[{"localIndex":1,"countryCode":"pl","olxUserId":7,"tokens":$LEGACY_TOKENS_JSON,"lastUsedAtEpochSeconds":10,"lastRefreshedAtEpochSeconds":10}],"activeByCountry":{"pl":1},"nextLocalIndex":2}"""
    }
}
