package com.sirelon.sellsnap.features.auth.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxTokenStore
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
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
        val tokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply {
            write(
                OlxTokens(
                    accessToken = "active-token",
                    refreshToken = "refresh-token",
                    expiresInSeconds = 86_400,
                    tokenType = "bearer",
                    scope = "v2 read write",
                    issuedAtEpochSeconds = 4_102_444_800,
                ),
            )
        }
        val holder = createRepository(
            tokenStore = tokenStore,
            engine = MockEngine { request ->
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
            },
        )
        val errorParser = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(holder.engine),
                credentialsProvider = TestCredentialsProvider(),
                tokenStore = tokenStore,
                authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
                errorParser = errorParser,
                engine = holder.engine,
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
        val tokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply {
            write(
                OlxTokens(
                    accessToken = "active-token",
                    refreshToken = "refresh-token",
                    expiresInSeconds = 86_400,
                    tokenType = "bearer",
                    scope = "v2 read write",
                    issuedAtEpochSeconds = 4_102_444_800,
                ),
            )
        }
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
                tokenStore = tokenStore,
                authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
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
        val tokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply {
            write(
                OlxTokens(
                    accessToken = "stale-token",
                    refreshToken = "refresh-token",
                    expiresInSeconds = 86_400,
                    tokenType = "bearer",
                    scope = "v2 read write",
                    issuedAtEpochSeconds = 4_102_444_800,
                ),
            )
        }
        val holder = createRepository(tokenStore = tokenStore, engine = engine)
        val errorParser3 = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                tokenStore = tokenStore,
                authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
                errorParser = errorParser3,
                engine = engine,
            ),
            json = testJson,
            errorParser = errorParser3,
        )

        apiClient.getAuthenticatedUser()

        assertEquals(listOf<String?>("Bearer stale-token", "Bearer refreshed-token"), seenAuthorizationHeaders)
        assertEquals("refreshed-token", tokenStore.read()?.accessToken)
    }

    @Test
    fun `getAuthenticatedUser clears persisted tokens when refresh returns invalid_grant`() = runBlocking {
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
        val tokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply {
            write(
                OlxTokens(
                    accessToken = "stale-token",
                    refreshToken = "bad-refresh-token",
                    expiresInSeconds = 86_400,
                    tokenType = "bearer",
                    scope = "v2 read write",
                    issuedAtEpochSeconds = 4_102_444_800,
                ),
            )
        }
        val errorParser4 = OlxRemoteErrorParser(testJson)
        val apiClient = OlxApiClient(
            httpClient = createOlxAuthorizedHttpClient(
                authRefreshClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                tokenStore = tokenStore,
                authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
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
        assertNull(tokenStore.read())
    }

    @Test
    fun `remote error parser keeps status detail when error response is empty`() {
        val exception = OlxRemoteErrorParser(testJson).parse(HttpStatusCode.BadGateway, "")

        val error = assertIs<OlxApiError.Unknown>(exception.error)
        assertTrue(error.userMessage.contains("HTTP 502"))
        assertTrue(error.userMessage.contains("empty"))
    }

    private fun createRepository(
        tokenStore: OlxTokenStore,
        engine: MockEngine,
    ): TestRepositoryHolder {
        return TestRepositoryHolder(
            engine = engine,
            repository = OlxAuthRepository(
                httpClient = createOlxHttpClient(engine),
                credentialsProvider = TestCredentialsProvider(),
                tokenStore = tokenStore,
                authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
                redirectHandler = TestRedirectHandler(),
                guestModeStore = GuestModeStore(InMemoryOlxKeyValueStore()),
                errorParser = OlxRemoteErrorParser(testJson),
            ),
        )
    }

    private data class TestRepositoryHolder(
        val engine: MockEngine,
        val repository: OlxAuthRepository,
    )

    private class TestCredentialsProvider : OlxCredentialsProvider {
        override suspend fun getClientId(): String = "test-client-id"

        override suspend fun getClientSecret(): String = "test-client-secret"
    }

    private class TestRedirectHandler : OlxRedirectHandler {
        override fun buildRedirectUri(platform: com.sirelon.sellsnap.platform.PlatformTargets): String {
            return "selolxai://olx-auth/callback"
        }

        override fun parseCallback(url: String): OlxAuthCallback {
            val parsed = io.ktor.http.Url(url)
            return OlxAuthCallback(
                code = parsed.parameters["code"],
                state = parsed.parameters["state"],
                error = parsed.parameters["error"],
                errorDescription = parsed.parameters["error_description"],
            )
        }
    }
}
