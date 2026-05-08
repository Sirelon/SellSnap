package com.sirelon.aicalories.features.auth.data

import com.sirelon.aicalories.features.seller.auth.data.OlxAuthRepository
import com.sirelon.aicalories.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.aicalories.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.aicalories.features.seller.auth.data.GuestModeStore
import com.sirelon.aicalories.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.aicalories.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.aicalories.features.seller.auth.data.OlxTokenStore
import com.sirelon.aicalories.features.seller.auth.data.createOlxHttpClient
import kotlinx.serialization.json.Json
import com.sirelon.aicalories.features.seller.auth.domain.OlxApiError
import com.sirelon.aicalories.features.seller.auth.domain.OlxApiException
import com.sirelon.aicalories.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.aicalories.features.seller.auth.domain.OlxTokens
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class OlxAuthRepositoryTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `createAuthorizationRequest builds olx auth url and stores state`() = runBlocking {
        val sessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson)
        val repository = createRepository(
            engine = MockEngine { error("No HTTP call expected.") },
            sessionStore = sessionStore,
        )

        val request = repository.createAuthorizationRequest()
        val savedSession = sessionStore.read()

        assertContains(request.url, "response_type=code")
        assertContains(request.url, "client_id=test-client-id")
        assertContains(request.url, "scope=read+write+v2")
        assertContains(request.url, "redirect_uri=selolxai%3A%2F%2Folx-auth%2Fcallback")
        assertTrue(request.state.isNotBlank())
        assertEquals(savedSession?.state, request.state)
        assertEquals(savedSession?.redirectUri, request.redirectUri)
    }

    @Test
    fun `completeAuthorization rejects state mismatch`() = runBlocking {
        val repository = createRepository(engine = MockEngine { error("No HTTP call expected.") })
        val request = repository.createAuthorizationRequest()

        val result = repository.completeAuthorization("${request.redirectUri}?code=one-time-code&state=wrong")

        assertTrue(result.isFailure)
        assertIs<OlxApiException>(result.exceptionOrNull())
        assertIs<OlxApiError.InvalidState>((result.exceptionOrNull() as OlxApiException).error)
        Unit
    }

    @Test
    fun `completeAuthorization exchanges authorization code with expected payload`() = runBlocking {
        var requestBody = ""
        val repository = createRepository(
            engine = MockEngine { request ->
                requestBody = (request.body as TextContent).text
                respond(
                    content = """
                        {
                          "access_token": "new-access-token",
                          "refresh_token": "new-refresh-token",
                          "expires_in": 86400,
                          "token_type": "bearer",
                          "scope": "v2 read write"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val request = repository.createAuthorizationRequest()

        val result = repository.completeAuthorization("${request.redirectUri}?code=one-time-code&state=${request.state}")

        assertTrue(result.isSuccess)
        assertContains(requestBody, "\"grant_type\":\"authorization_code\"")
        assertContains(requestBody, "\"client_id\":\"test-client-id\"")
        assertContains(requestBody, "\"client_secret\":\"test-client-secret\"")
        assertContains(requestBody, "\"code\":\"one-time-code\"")
        assertContains(requestBody, "\"redirect_uri\":\"selolxai://olx-auth/callback\"")
    }

    @Test
    fun `refreshIfNeeded sends refresh token request and replaces stored tokens`() = runBlocking {
        var requestBody = ""
        val tokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply {
            write(
                OlxTokens(
                    accessToken = "expired-access-token",
                    refreshToken = "old-refresh-token",
                    expiresInSeconds = 10,
                    tokenType = "bearer",
                    scope = "v2 read write",
                    issuedAtEpochSeconds = 0,
                ),
            )
        }
        val repository = createRepository(
            engine = MockEngine { request ->
                requestBody = (request.body as TextContent).text
                respond(
                    content = """
                        {
                          "access_token": "fresh-access-token",
                          "refresh_token": "fresh-refresh-token",
                          "expires_in": 86400,
                          "token_type": "bearer",
                          "scope": "v2 read write"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
            tokenStore = tokenStore,
        )

        val result = repository.refreshIfNeeded(force = true)
        val storedTokens = tokenStore.read()

        assertTrue(result.isSuccess)
        assertContains(requestBody, "\"grant_type\":\"refresh_token\"")
        assertContains(requestBody, "\"refresh_token\":\"old-refresh-token\"")
        assertEquals("fresh-access-token", storedTokens?.accessToken)
        assertEquals("fresh-refresh-token", storedTokens?.refreshToken)
    }

    @Test
    fun `refreshIfNeeded clears stored tokens on invalid grant`() = runBlocking {
        val tokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson).apply {
            write(
                OlxTokens(
                    accessToken = "expired-access-token",
                    refreshToken = "stale-refresh-token",
                    expiresInSeconds = 10,
                    tokenType = "bearer",
                    scope = "v2 read write",
                    issuedAtEpochSeconds = 0,
                ),
            )
        }
        val repository = createRepository(
            engine = MockEngine {
                respond(
                    content = """
                        {
                          "error": "invalid_grant",
                          "error_description": "Refresh token is invalid"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
            tokenStore = tokenStore,
        )

        val result = repository.refreshIfNeeded(force = true)

        assertTrue(result.isFailure)
        assertIs<OlxApiException>(result.exceptionOrNull())
        assertIs<OlxApiError.InvalidGrant>((result.exceptionOrNull() as OlxApiException).error)
        assertNull(tokenStore.read())
    }

    private fun createRepository(
        engine: MockEngine,
        tokenStore: OlxTokenStore = OlxTokenStore(InMemoryOlxKeyValueStore(), testJson),
        sessionStore: OlxAuthSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
    ): OlxAuthRepository {
        return OlxAuthRepository(
            httpClient = createOlxHttpClient(engine),
            credentialsProvider = TestCredentialsProvider(),
            tokenStore = tokenStore,
            authSessionStore = sessionStore,
            redirectHandler = TestRedirectHandler(),
            guestModeStore = GuestModeStore(InMemoryOlxKeyValueStore()),
            errorParser = OlxRemoteErrorParser(testJson),
        )
    }

    private class TestCredentialsProvider : OlxCredentialsProvider {
        override suspend fun getClientId(): String = "test-client-id"

        override suspend fun getClientSecret(): String = "test-client-secret"
    }

    private class TestRedirectHandler : OlxRedirectHandler {
        override fun buildRedirectUri(platform: com.sirelon.aicalories.platform.PlatformTargets): String {
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
