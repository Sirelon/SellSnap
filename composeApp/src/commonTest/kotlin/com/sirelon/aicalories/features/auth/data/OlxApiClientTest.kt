package com.sirelon.sellsnap.features.auth.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.features.seller.currency.data.CurrencyRepository
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class OlxApiClientTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `getAuthenticatedUser attaches bearer token and version header`() = runBlocking {
        var authorizationHeader: String? = null
        var versionHeader: String? = null
        val accountStore = accountStoreWithActiveTokens(sampleTokens("active-token"))
        val engine = MockEngine { request ->
            authorizationHeader = request.headers[HttpHeaders.Authorization]
            versionHeader = request.headers["Version"]
            respond(
                content = """
                    {
                      "data": {
                        "id": 77,
                        "email": "seller@example.com",
                        "name": "Seller"
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val errorParser = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                accountStore = accountStore,
                countryStore = countryStore(),
                errorParser = errorParser,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser,
        )
        apiClient.getAuthenticatedUser()

        assertEquals("Bearer active-token", authorizationHeader)
        assertEquals("2.0", versionHeader)
    }

    @Test
    fun `getAuthenticatedUser maps olx user response fields`() = runBlocking {
        val accountStore = accountStoreWithActiveTokens(sampleTokens("active-token"))
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "data": {
                        "id": 77,
                        "email": "seller@example.com",
                        "status": "confirmed",
                        "name": "Seller",
                        "phone": "+380501112233",
                        "created_at": "2026-01-01T10:00:00+02:00",
                        "last_login_at": "2026-04-28T11:00:00+02:00",
                        "avatar": "https://example.com/avatar.png",
                        "is_business": true
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val errorParser2 = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                accountStore = accountStore,
                countryStore = countryStore(),
                errorParser = errorParser2,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser2,
        )

        val user = apiClient.getAuthenticatedUser()

        assertEquals(77L, user.id)
        assertEquals("seller@example.com", user.email)
        assertEquals("confirmed", user.status)
        assertEquals("Seller", user.name)
        assertEquals("+380501112233", user.phone)
        assertEquals("2026-01-01T10:00:00+02:00", user.createdAt)
        assertEquals("2026-04-28T11:00:00+02:00", user.lastLoginAt)
        assertEquals("https://example.com/avatar.png", user.avatar)
        assertEquals(true, user.isBusiness)
    }

    @Test
    fun `getAuthenticatedUser via explicit access token attaches that token on an unauthenticated client`() = runBlocking {
        // Mirrors real usage (SellerAccountRepository.addAccount): the explicit-token overload is
        // only ever called on the plain/unauthenticated client, never one with the Auth plugin
        // installed - the freshly exchanged token isn't in the account store yet, so there is no
        // account for a bearer provider to resolve it from.
        var authorizationHeader: String? = null
        val engine = MockEngine { request ->
            authorizationHeader = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{ "data": { "id": 99, "email": "fresh@example.com", "name": "Fresh" } }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val errorParser = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxHttpClient(engine),
            json = testJson,
            errorParser = errorParser,
        )

        val user = apiClient.getAuthenticatedUser(accessToken = "explicit-token")

        assertEquals("Bearer explicit-token", authorizationHeader)
        assertEquals(99L, user.id)
    }

    @Test
    fun `getAuthenticatedUser refreshes and retries after invalid token response`() = runBlocking {
        var userRequestCount = 0
        var seenAuthorizationHeaders = mutableListOf<String?>()
        val engine = MockEngine { request ->
            when {
                request.url.toString().contains("/partner/users/me") -> {
                    userRequestCount += 1
                    seenAuthorizationHeaders += request.headers[HttpHeaders.Authorization]
                    if (userRequestCount == 1) {
                        respond(
                            content = """
                                {
                                  "error": "invalid_token",
                                  "error_description": "The access token provided is invalid"
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.Unauthorized,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    } else {
                        respond(
                            content = """
                                {
                                  "data": {
                                    "id": 88,
                                    "email": "seller@example.com",
                                    "name": "Seller"
                                  }
                                }
                            """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }

                request.url.toString().contains("/open/oauth/token") -> {
                    respond(
                        content = """
                            {
                              "access_token": "refreshed-token",
                              "refresh_token": "new-refresh-token",
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
        val accountStore = accountStoreWithActiveTokens(sampleTokens("stale-token"))
        val errorParser3 = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                accountStore = accountStore,
                countryStore = countryStore(),
                errorParser = errorParser3,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser3,
        )

        apiClient.getAuthenticatedUser()

        assertEquals(listOf<String?>("Bearer stale-token", "Bearer refreshed-token"), seenAuthorizationHeaders)
        assertEquals("refreshed-token", accountStore.readRaw()!!.accounts.single().tokens.accessToken)
    }

    @Test
    fun `getAuthenticatedUser marks the account NeedsReconnect when refresh returns invalid_grant`() = runBlocking {
        var refreshRequestCount = 0
        val engine = MockEngine { request ->
            when {
                request.url.toString().contains("/partner/users/me") -> {
                    respond(
                        content = """
                            {
                              "error": "invalid_token",
                              "error_description": "The access token provided is invalid"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                request.url.toString().contains("/open/oauth/token") -> {
                    refreshRequestCount += 1
                    respond(
                        content = """
                            {
                              "error": "invalid_grant",
                              "error_description": "Refresh token expired"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

                else -> error("Unexpected request: ${request.url}")
            }
        }
        val accountStore = accountStoreWithActiveTokens(sampleTokens("stale-token", refreshToken = "bad-refresh-token"))
        val errorParser4 = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                accountStore = accountStore,
                countryStore = countryStore(),
                errorParser = errorParser4,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser4,
        )

        assertFailsWith<OlxApiException> {
            apiClient.getAuthenticatedUser()
        }
        assertEquals(1, refreshRequestCount)
        assertEquals(OlxAccountState.NeedsReconnect, accountStore.readRaw()!!.accounts.single().state)
    }

    @Test
    fun `getCurrentUserAdverts attaches auth headers paging params and maps adverts`() = runBlocking {
        var authorizationHeader: String? = null
        var versionHeader: String? = null
        var offsetParameter: String? = null
        var limitParameter: String? = null
        val engine = MockEngine { request ->
            authorizationHeader = request.headers[HttpHeaders.Authorization]
            versionHeader = request.headers["Version"]
            offsetParameter = request.url.parameters["offset"]
            limitParameter = request.url.parameters["limit"]
            respond(
                content = """
                    {
                      "data": [
                        {
                          "id": 1001,
                          "status": "active",
                          "url": "https://www.olx.ua/d/uk/obyavlenie/bike-ID1001.html",
                          "created_at": "2026-05-01T10:00:00+03:00",
                          "valid_to": "2026-06-01T10:00:00+03:00",
                          "title": "City bike",
                          "images": [
                            { "url": "https://example.com/bike.jpg" }
                          ],
                          "price": {
                            "value": 1500,
                            "currency": "UAH",
                            "negotiable": false
                          }
                        },
                        {
                          "id": null,
                          "title": "Missing identity"
                        },
                        {
                          "id": 1002,
                          "status": "limited",
                          "url": null,
                          "created_at": null,
                          "valid_to": null,
                          "title": null,
                          "images": [
                            { "url": "" },
                            { "url": "https://example.com/phone.jpg" }
                          ],
                          "price": {
                            "value": null,
                            "currency": "UAH",
                            "negotiable": null
                          }
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val accountStore = accountStoreWithActiveTokens(sampleTokens("active-token"))
        val errorParser = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                accountStore = accountStore,
                countryStore = countryStore(),
                errorParser = errorParser,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser,
        )

        val adverts = apiClient.getCurrentUserAdverts(offset = 50, limit = 25)

        assertEquals("Bearer active-token", authorizationHeader)
        assertEquals("2.0", versionHeader)
        assertEquals("50", offsetParameter)
        assertEquals("25", limitParameter)
        assertEquals(2, adverts.size)
        assertEquals(1001L, adverts[0].id)
        assertEquals("City bike", adverts[0].title)
        assertEquals(AdvertStatus.Active, adverts[0].status)
        assertEquals("https://www.olx.ua/d/uk/obyavlenie/bike-ID1001.html", adverts[0].url)
        assertEquals("https://example.com/bike.jpg", adverts[0].primaryImageUrl)
        assertEquals(1500L, adverts[0].price?.value)
        assertEquals("UAH", adverts[0].price?.currency)
        assertEquals(false, adverts[0].price?.negotiable)
        assertEquals(1002L, adverts[1].id)
        assertEquals("", adverts[1].title)
        assertEquals(AdvertStatus.Limited, adverts[1].status)
        assertEquals("", adverts[1].url)
        assertEquals("https://example.com/phone.jpg", adverts[1].primaryImageUrl)
        assertNull(adverts[1].price)
    }

    @Test
    fun `getCurrentUserAdverts surfaces olx api errors`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "error": "insufficient_scope",
                      "error_description": "Token does not include advert read scope"
                    }
                """.trimIndent(),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val accountStore = accountStoreWithActiveTokens(sampleTokens("active-token"))
        val errorParser = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                accountStore = accountStore,
                countryStore = countryStore(),
                errorParser = errorParser,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser,
        )

        val exception = assertFailsWith<OlxApiException> {
            apiClient.getCurrentUserAdverts(offset = 0, limit = 50)
        }

        assertIs<OlxApiError.InsufficientScope>(exception.error)
        Unit
    }

    @Test
    fun `loadCurrencies attaches auth headers and maps documented raw list`() = runBlocking {
        var authorizationHeader: String? = null
        var versionHeader: String? = null
        val engine = MockEngine { request ->
            authorizationHeader = request.headers[HttpHeaders.Authorization]
            versionHeader = request.headers["Version"]
            respond(
                content = """
                    [
                      { "code": "PLN", "label": "zł", "is_default": false },
                      { "code": "UAH", "label": "₴", "is_default": true },
                      { "code": null, "label": "broken", "is_default": false }
                    ]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val accountStore = accountStoreWithActiveTokens(sampleTokens("active-token"))
        val errorParser = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                accountStore = accountStore,
                countryStore = countryStore(),
                errorParser = errorParser,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser,
        )

        val currencies = apiClient.loadCurrencies()

        assertEquals("Bearer active-token", authorizationHeader)
        assertEquals("2.0", versionHeader)
        assertEquals(
            listOf(
                OlxCurrency(code = "PLN", label = "zł", isDefault = false),
                OlxCurrency(code = "UAH", label = "₴", isDefault = true),
            ),
            currencies,
        )
    }

    @Test
    fun `currency repository falls back to UAH when currencies are unavailable`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val accountStore = accountStoreWithActiveTokens(sampleTokens("active-token"))
        val errorParser = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                accountStore = accountStore,
                countryStore = countryStore(),
                errorParser = errorParser,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser,
        )

        val currency = CurrencyRepository(apiClient).getDefaultCurrency()

        assertEquals(OlxCurrency.Default, currency)
    }

    @Test
    fun `remote error parser keeps status detail when error response is empty`() {
        val exception = OlxRemoteErrorParser(testJson).parse(HttpStatusCode.BadGateway, "")

        val error = assertIs<OlxApiError.Unknown>(exception.error)
        assertTrue(error.userMessage.contains("HTTP 502"))
        assertTrue(error.userMessage.contains("empty"))
    }

    private fun sampleTokens(accessToken: String, refreshToken: String = "refresh-token") = OlxTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresInSeconds = 86_400,
        tokenType = "bearer",
        scope = "v2 read write",
        issuedAtEpochSeconds = 4_102_444_800,
    )

    private suspend fun accountStoreWithActiveTokens(tokens: OlxTokens, countryCode: String = "ua"): OlxAccountStore {
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
