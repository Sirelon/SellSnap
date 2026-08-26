package com.sirelon.sellsnap.features.auth.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import io.ktor.client.request.get
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Covers the item-1 requirements from the SIR-83 foundation task: the authorized client resolves
 * tokens for whichever account is active for the current country, refreshes exactly once under
 * concurrent 401s, and a terminal refresh failure only ever marks the ONE account it refreshed.
 */
class OlxHttpClientFactoryTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `valid token resolves with no refresh network call`() = runBlocking {
        var refreshCallCount = 0
        var authorizationHeader: String? = null
        val accountStore = singleAccountStore(sampleTokens("valid-token"))
        val engine = MockEngine { request ->
            if (request.url.toString().contains("/open/oauth/token")) refreshCallCount += 1
            authorizationHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = createOlxAuthorizedHttpClient(
            authRefreshClient = createOlxHttpClient(engine),
            credentialsProvider = TestCredentialsProvider(),
            accountStore = accountStore,
            countryStore = countryStore(),
            errorParser = OlxRemoteErrorParser(testJson),
            engine = engine,
        )

        client.get("users/me")

        assertEquals("Bearer valid-token", authorizationHeader)
        assertEquals(0, refreshCallCount)
    }

    @Test
    fun `expired token triggers exactly one refresh under concurrent callers`() = runBlocking {
        val accountStore = singleAccountStore(sampleTokens("stale-token"))
        var refreshCallCount = 0
        val refreshMutex = Mutex()
        val engine = MockEngine { request ->
            when {
                request.url.toString().contains("/partner/users/me") -> {
                    val authorized = request.headers[HttpHeaders.Authorization] == "Bearer fresh-token"
                    respond(
                        content = "{}",
                        status = if (authorized) HttpStatusCode.OK else HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                request.url.toString().contains("/open/oauth/token") -> {
                    refreshMutex.withLock { refreshCallCount += 1 }
                    respond(
                        content = """
                            {
                              "access_token": "fresh-token",
                              "refresh_token": "fresh-refresh-token",
                              "expires_in": 86400,
                              "token_type": "bearer",
                              "scope": "v2 read write"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                else -> error("Unexpected request: ${request.url}")
            }
        }
        val client = createOlxAuthorizedHttpClient(
            authRefreshClient = createOlxHttpClient(engine),
            credentialsProvider = TestCredentialsProvider(),
            accountStore = accountStore,
            countryStore = countryStore(),
            errorParser = OlxRemoteErrorParser(testJson),
            engine = engine,
        )

        coroutineScope {
            (1..5).map { async { client.get("users/me") } }.awaitAll()
        }

        assertEquals(1, refreshCallCount)
        assertEquals("fresh-token", accountStore.readRaw()!!.accounts.single().tokens.accessToken)
    }

    @Test
    fun `invalid_grant on the active account marks only that account NeedsReconnect`() = runBlocking {
        val untouchedAccount = OlxAccountRecord(
            localIndex = 1,
            countryCode = "pl",
            tokens = sampleTokens("pl-token"),
            lastUsedAtEpochSeconds = 111,
            lastRefreshedAtEpochSeconds = 111,
        )
        val activeAccount = OlxAccountRecord(
            localIndex = 2,
            countryCode = "ua",
            tokens = sampleTokens("ua-stale-token", refreshToken = "bad-refresh-token"),
            lastUsedAtEpochSeconds = 222,
            lastRefreshedAtEpochSeconds = 222,
        )
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(untouchedAccount, activeAccount),
                activeByCountry = mapOf("pl" to 1, "ua" to 2),
                nextLocalIndex = 3,
            ),
        )
        val engine = MockEngine { request ->
            when {
                request.url.toString().contains("/partner/users/me") -> respond(
                    content = """{"error":"invalid_token","error_description":"dead"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                request.url.toString().contains("/open/oauth/token") -> respond(
                    content = """{"error":"invalid_grant","error_description":"refresh token expired"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )

                else -> error("Unexpected request: ${request.url}")
            }
        }
        val client = createOlxAuthorizedHttpClient(
            authRefreshClient = createOlxHttpClient(engine),
            credentialsProvider = TestCredentialsProvider(),
            accountStore = accountStore,
            countryStore = countryStore("ua"),
            errorParser = OlxRemoteErrorParser(testJson),
            engine = engine,
        )

        assertFailsWith<OlxApiException> { client.get("users/me") }

        val record = accountStore.readRaw()!!
        assertEquals(untouchedAccount, record.accounts.find { it.localIndex == 1 })
        val refreshedActive = record.accounts.find { it.localIndex == 2 }!!
        assertEquals(OlxAccountState.NeedsReconnect, refreshedActive.state)
    }

    private fun sampleTokens(accessToken: String, refreshToken: String = "refresh-token") = OlxTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresInSeconds = 86_400,
        tokenType = "bearer",
        scope = "v2 read write",
        issuedAtEpochSeconds = 0,
    )

    private suspend fun singleAccountStore(tokens: OlxTokens, countryCode: String = "ua"): OlxAccountStore {
        val store = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        store.write(
            OlxAccountsRecord(
                accounts = listOf(
                    OlxAccountRecord(
                        localIndex = 1,
                        countryCode = countryCode,
                        tokens = tokens,
                        lastUsedAtEpochSeconds = 0,
                        lastRefreshedAtEpochSeconds = 0,
                    ),
                ),
                activeByCountry = mapOf(countryCode to 1),
                nextLocalIndex = 2,
            ),
        )
        return store
    }

    private suspend fun countryStore(countryCode: String = "ua"): OlxCountryStore =
        OlxCountryStore(InMemoryOlxKeyValueStore(), FakeAnalytics()).apply { save(OlxCountry.fromCode(countryCode)!!) }

    private class TestCredentialsProvider : OlxCredentialsProvider {
        override suspend fun getClientId(): String = "test-client-id"

        override suspend fun getClientSecret(): String = "test-client-secret"
    }

    private class FakeAnalytics : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }
}
