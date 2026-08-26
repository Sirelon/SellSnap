package com.sirelon.sellsnap.features.auth.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import kotlinx.serialization.json.Json
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
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

        val result = runCatching {
            repository.exchangeAuthorizationCallback("${request.redirectUri}?code=one-time-code&state=wrong")
        }

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

        val tokens = repository.exchangeAuthorizationCallback("${request.redirectUri}?code=one-time-code&state=${request.state}")

        assertEquals("new-access-token", tokens.accessToken)
        assertContains(requestBody, "\"grant_type\":\"authorization_code\"")
        assertContains(requestBody, "\"client_id\":\"test-client-id\"")
        assertContains(requestBody, "\"client_secret\":\"test-client-secret\"")
        assertContains(requestBody, "\"code\":\"one-time-code\"")
        assertContains(requestBody, "\"redirect_uri\":\"selolxai://olx-auth/callback\"")
    }

    @Test
    fun `completeAuthorization does not persist tokens or touch session mode`() = runBlocking {
        // exchangeAuthorizationCallback is intentionally a pure exchange step now - persistence
        // and session-mode emission moved to SellerAccountRepository.addAccount, since only that
        // layer knows (after a users-me call) whether the token belongs to a duplicate account.
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        val repository = createRepository(
            engine = MockEngine {
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
            accountStore = accountStore,
        )
        val request = repository.createAuthorizationRequest()

        repository.exchangeAuthorizationCallback("${request.redirectUri}?code=one-time-code&state=${request.state}")

        assertEquals(SellerSessionMode.Unauthenticated, repository.currentSession().mode)
        assertTrue(accountStore.recordFlow.value.accounts.isEmpty())
    }

    @Test
    fun `currentSession reports Authenticated whenever any account is on file for the active country regardless of token health`() =
        runBlocking {
            val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), FakeAnalytics()).apply { save(OlxCountry.UA) }
            val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
            accountStore.addOrUpdateAccount(
                countryCode = "ua",
                olxUserId = 1L,
                tokens = sampleTokens(accessToken = "dead-token"),
                profile = null,
                makeActive = true,
            )
            accountStore.markNeedsReconnect(1)
            val repository = createRepository(
                engine = MockEngine { error("No HTTP call expected.") },
                accountStore = accountStore,
                countryStore = countryStore,
            )

            val session = repository.currentSession()

            assertEquals(SellerSessionMode.Authenticated, session.mode)
        }

    @Test
    fun `currentSession reports Unauthenticated when no account is on file for the active country`() = runBlocking {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), FakeAnalytics()).apply { save(OlxCountry.UA) }
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        val repository = createRepository(
            engine = MockEngine { error("No HTTP call expected.") },
            accountStore = accountStore,
            countryStore = countryStore,
        )

        val session = repository.currentSession()

        assertEquals(SellerSessionMode.Unauthenticated, session.mode)
    }

    private fun sampleTokens(accessToken: String = "access-token") = com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens(
        accessToken = accessToken,
        refreshToken = "refresh-token",
        expiresInSeconds = 86400,
        tokenType = "bearer",
        scope = "v2 read write",
        issuedAtEpochSeconds = 0,
    )

    private fun createRepository(
        engine: MockEngine,
        accountStore: OlxAccountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson),
        countryStore: OlxCountryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), FakeAnalytics()),
        sessionStore: OlxAuthSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
    ): OlxAuthRepository {
        return OlxAuthRepository(
            httpClient = createOlxHttpClient(engine),
            credentialsProvider = TestCredentialsProvider(),
            accountStore = accountStore,
            countryStore = countryStore,
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

    private class FakeAnalytics : Analytics {
        override fun logEvent(name: String, params: Map<String, Any>) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
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
