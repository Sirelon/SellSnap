package com.sirelon.sellsnap.features.seller.categories.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAppHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class CategoriesRepositoryTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `country change between two loads resolves against the new country's tree`() = runBlocking {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), FakeAnalytics())
        countryStore.save(OlxCountry.UA)
        val categoryRequestsByHost = mutableMapOf<String, Int>()
        val engine = MockEngine { request ->
            val host = request.url.host
            val body = when {
                request.url.encodedPath.endsWith("categories/suggestion") ->
                    """{"data":[{"id":"$PL_CATEGORY_ID"}]}"""
                request.url.encodedPath.endsWith("categories") -> {
                    categoryRequestsByHost[host] = (categoryRequestsByHost[host] ?: 0) + 1
                    when (host) {
                        "www.olx.ua" -> """{"data":[{"id":$UA_CATEGORY_ID,"name":"UA","parent_id":0,"is_leaf":true}]}"""
                        "www.olx.pl" -> """{"data":[{"id":$PL_CATEGORY_ID,"name":"PL","parent_id":0,"is_leaf":true}]}"""
                        else -> error("Unexpected host $host")
                    }
                }
                else -> error("Unexpected request ${request.url}")
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val errorParser = OlxRemoteErrorParser(testJson)
        val olxApiClient = OlxApiClient(httpClient = createOlxHttpClient(engine), json = testJson, errorParser = errorParser)
        val repository = CategoriesRepository(
            olxApiClient = olxApiClient,
            appApiClient = olxApiClient,
            accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson),
            mapper = CategoriesMapper(),
            countryStore = countryStore,
        )

        assertEquals(listOf(UA_CATEGORY_ID), repository.loadCategories().first().map { it.id })

        countryStore.save(OlxCountry.PL)

        assertEquals(PL_CATEGORY_ID, repository.categorySuggestion("Sneakers").first().id)
        assertEquals(mapOf("www.olx.ua" to 1, "www.olx.pl" to 1), categoryRequestsByHost)
    }

    @Test
    fun `a failed fetch while offline does not replace a tree that already loaded`() = runBlocking {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), FakeAnalytics())
        countryStore.save(OlxCountry.UA)
        var offline = false
        val engine = MockEngine {
            if (offline) throw IOException("offline")
            respond(
                content = """{"data":[{"id":$UA_CATEGORY_ID,"name":"UA","parent_id":0,"is_leaf":true}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val errorParser = OlxRemoteErrorParser(testJson)
        val olxApiClient = OlxApiClient(httpClient = createOlxHttpClient(engine), json = testJson, errorParser = errorParser)
        val repository = CategoriesRepository(
            olxApiClient = olxApiClient,
            appApiClient = olxApiClient,
            accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson),
            mapper = CategoriesMapper(),
            countryStore = countryStore,
        )

        repository.loadCategories().first()
        // The seller goes offline, opens the category picker and picks a category: the pick reads
        // the tree again for the category's path label, and on iOS a throw there ends the app.
        offline = true
        repository.loadCategories().first()

        assertEquals(UA_CATEGORY_ID, repository.getCategoryById(UA_CATEGORY_ID)?.id)
    }

    @Test
    fun `a seller token that dies mid-call falls back to the app token`() = runBlocking {
        val harness = expiredConnectionHarness(OlxAccountState.Usable)

        assertEquals(UA_CATEGORY_ID, harness.repository.categorySuggestion("Sneakers").first().id)
        assertEquals(emptyList(), harness.repository.getAttributes(UA_CATEGORY_ID).first())

        assertEquals(OlxAccountState.NeedsReconnect, harness.accountStore.recordFlow.value.accounts.single().state)
        // One refresh, refused; after that the account is known dead and nothing retries it.
        assertEquals(1, harness.refreshCalls)
    }

    @Test
    fun `an account already marked NeedsReconnect reads the catalog with the app token only`() = runBlocking {
        val harness = expiredConnectionHarness(OlxAccountState.NeedsReconnect)

        assertEquals(UA_CATEGORY_ID, harness.repository.categorySuggestion("Sneakers").first().id)
        assertEquals(emptyList(), harness.repository.getAttributes(UA_CATEGORY_ID).first())

        assertEquals(0, harness.refreshCalls)
        assertEquals(0, harness.sellerTokenCalls)
    }

    private class ExpiredConnectionHarness(val repository: CategoriesRepository, val accountStore: OlxAccountStore) {
        var refreshCalls = 0
        var sellerTokenCalls = 0
    }

    /** OLX as it answers a seller whose connection expired: the seller's access token gets 401,
     * its refresh gets `invalid_grant`, and only the app's client_credentials token reads. */
    private suspend fun expiredConnectionHarness(state: OlxAccountState): ExpiredConnectionHarness {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), FakeAnalytics())
        countryStore.save(OlxCountry.UA)
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(
                    OlxAccountRecord(
                        localIndex = 1,
                        countryCode = "ua",
                        tokens = OlxTokens(
                            accessToken = "dead-token",
                            refreshToken = "dead-refresh-token",
                            expiresInSeconds = 86_400,
                            tokenType = "bearer",
                            scope = "v2 read write",
                            issuedAtEpochSeconds = 0,
                        ),
                        lastUsedAtEpochSeconds = 0,
                        lastRefreshedAtEpochSeconds = 0,
                        state = state,
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )
        lateinit var harness: ExpiredConnectionHarness
        val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.endsWith("open/oauth/token")) {
                val body = request.body.toByteArray().decodeToString()
                return@MockEngine if (body.contains("\"client_credentials\"")) {
                    respond("""{"access_token":"app-token","expires_in":900,"token_type":"bearer","scope":"v2 read"}""", HttpStatusCode.OK, json)
                } else {
                    harness.refreshCalls += 1
                    respond("""{"error":"invalid_grant","error_description":"refresh token expired"}""", HttpStatusCode.BadRequest, json)
                }
            }
            if (request.headers[HttpHeaders.Authorization] != "Bearer app-token") {
                harness.sellerTokenCalls += 1
                return@MockEngine respond("""{"error":"invalid_token","error_description":"dead"}""", HttpStatusCode.Unauthorized, json)
            }
            val body = when {
                path.endsWith("categories/suggestion") -> """{"data":[{"id":"$UA_CATEGORY_ID"}]}"""
                path.endsWith("attributes") -> """{"data":[]}"""
                path.endsWith("categories") -> """{"data":[{"id":$UA_CATEGORY_ID,"name":"UA","parent_id":0,"is_leaf":true}]}"""
                else -> error("Unexpected request ${request.url}")
            }
            respond(body, HttpStatusCode.OK, json)
        }
        val errorParser = OlxRemoteErrorParser(testJson)
        val credentials = object : OlxCredentialsProvider {
            override suspend fun getClientId(): String = "test-client-id"
            override suspend fun getClientSecret(): String = "test-client-secret"
        }
        val sellerClient = createOlxAuthorizedHttpClient(
            authRefreshClient = createOlxHttpClient(engine),
            credentialsProvider = credentials,
            accountStore = accountStore,
            countryStore = countryStore,
            errorParser = errorParser,
            engine = engine,
        )
        val appClient = createOlxAppHttpClient(
            authRefreshClient = createOlxHttpClient(engine),
            credentialsProvider = credentials,
            errorParser = errorParser,
            engine = engine,
        )
        val repository = CategoriesRepository(
            olxApiClient = OlxApiClient(httpClient = sellerClient, json = testJson, errorParser = errorParser),
            appApiClient = OlxApiClient(httpClient = appClient, json = testJson, errorParser = errorParser),
            accountStore = accountStore,
            mapper = CategoriesMapper(),
            countryStore = countryStore,
        )
        harness = ExpiredConnectionHarness(repository, accountStore)
        return harness
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

    private companion object {
        const val UA_CATEGORY_ID = 101
        const val PL_CATEGORY_ID = 202
    }
}
