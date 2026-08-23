package com.sirelon.sellsnap.features.auth.data

import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.media.upload.DraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import com.sirelon.sellsnap.features.media.upload.PersistedDraftPhoto
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxProfileSnapshot
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.features.seller.location.LocationProvider
import com.sirelon.sellsnap.features.seller.location.DeviceLocation
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.location.data.LocationStore
import com.sirelon.sellsnap.features.seller.profile.data.AddAccountFailureReason
import com.sirelon.sellsnap.features.seller.profile.data.AddAccountOutcome
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class SellerAccountRepositoryTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `setActiveAccount clears the bearer cache so the next request uses the new active account`() = runBlocking {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(
                    account(localIndex = 1, olxUserId = 1L, accessToken = "token-a"),
                    account(localIndex = 2, olxUserId = 2L, accessToken = "token-b"),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 3,
            ),
        )
        var lastAuthorizationHeader: String? = null
        val engine = MockEngine { request ->
            lastAuthorizationHeader = request.headers[HttpHeaders.Authorization]
            respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val harness = harness(engine, accountStore)

        runCatching { harness.olxApiClient.getAuthenticatedUser() }
        assertEquals("Bearer token-a", lastAuthorizationHeader)

        harness.repository.setActiveAccount("ua", 2)
        runCatching { harness.olxApiClient.getAuthenticatedUser() }

        assertEquals("Bearer token-b", lastAuthorizationHeader)
        assertTrue(harness.analytics.events.any { it.first == AnalyticsEvents.ACCOUNT_SWITCHED })
    }

    @Test
    fun `setActiveAccount repopulates user from the cached profile with no network call`() = runBlocking {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(
                    account(
                        localIndex = 1, olxUserId = 1L, accessToken = "token-a",
                        profile = OlxProfileSnapshot(name = "Seller A", email = "a@example.com", avatarUrl = null, isBusiness = false),
                    ),
                    account(
                        localIndex = 2, olxUserId = 2L, accessToken = "token-b",
                        profile = OlxProfileSnapshot(name = "Seller B", email = "b@example.com", avatarUrl = null, isBusiness = true),
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 3,
            ),
        )
        var networkRequests = 0
        val engine = MockEngine {
            networkRequests += 1
            respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val harness = harness(engine, accountStore)

        harness.repository.setActiveAccount("ua", 2)

        assertEquals(0, networkRequests, "switching accounts must not make a network call (PRD U4)")
        val user = harness.repository.user.value
        assertEquals(2L, user?.id)
        assertEquals("Seller B", user?.name)
        assertEquals(true, user?.isBusiness)
    }

    @Test
    fun `disconnecting the active account repopulates user from the promoted account's cached profile`() = runBlocking {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(
                    account(
                        localIndex = 1, olxUserId = 1L, accessToken = "token-a", lastUsedAtEpochSeconds = 100,
                        profile = OlxProfileSnapshot(name = "Seller A", email = "a@example.com", avatarUrl = null, isBusiness = false),
                    ),
                    account(
                        localIndex = 2, olxUserId = 2L, accessToken = "token-b", lastUsedAtEpochSeconds = 300,
                        profile = OlxProfileSnapshot(name = "Seller B", email = "b@example.com", avatarUrl = null, isBusiness = true),
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 3,
            ),
        )
        val harness = harness(MockEngine { respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders()) }, accountStore)

        harness.repository.disconnectAccount("ua", 1)

        val user = harness.repository.user.value
        assertEquals(2L, user?.id)
        assertEquals("Seller B", user?.name)
    }

    @Test
    fun `addAccount adds a brand-new account and makes it active`() = runBlocking {
        val engine = tokenAndProfileEngine(profileOlxUserId = 501L)
        val harness = harness(engine, OlxAccountStore(InMemoryOlxKeyValueStore(), testJson))

        val outcome = completeAddAccount(harness)

        val added = assertIs<AddAccountOutcome.Added>(outcome)
        assertEquals(501L, added.account.olxUserId)
        val record = harness.accountStore.readRaw()!!
        assertEquals(1, record.accounts.size)
        assertEquals(added.account.localIndex, record.activeByCountry["ua"])
    }

    @Test
    fun `addAccount dedupes an existing olxUserId, reconnects it and marks it Usable`() = runBlocking {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(
                    account(localIndex = 1, olxUserId = 501L, accessToken = "old-token", state = OlxAccountState.NeedsReconnect),
                ),
                activeByCountry = emptyMap(),
                nextLocalIndex = 2,
            ),
        )
        val engine = tokenAndProfileEngine(profileOlxUserId = 501L)
        val harness = harness(engine, accountStore)

        val outcome = completeAddAccount(harness)

        val duplicate = assertIs<AddAccountOutcome.ReconnectedDuplicate>(outcome)
        assertEquals(1, duplicate.account.localIndex)
        val record = harness.accountStore.readRaw()!!
        assertEquals(1, record.accounts.size)
        assertEquals(OlxAccountState.Usable, record.accounts.single().state)
        assertEquals(1, record.activeByCountry["ua"])
    }

    @Test
    fun `addAccount rejects a genuinely new account at the cap without persisting it or throttling`() = runBlocking {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(
                    account(localIndex = 1, olxUserId = 1L, accessToken = "t1"),
                    account(localIndex = 2, olxUserId = 2L, accessToken = "t2"),
                    account(localIndex = 3, olxUserId = 3L, accessToken = "t3"),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 4,
            ),
        )
        val engine = tokenAndProfileEngine(profileOlxUserId = 999L)
        val harness = harness(engine, accountStore)

        val outcome = completeAddAccount(harness)

        val failed = assertIs<AddAccountOutcome.Failed>(outcome)
        assertEquals(AddAccountFailureReason.AccountLimitReached, failed.reason)
        val record = harness.accountStore.readRaw()!!
        assertEquals(3, record.accounts.size)
        assertTrue(record.accounts.none { it.olxUserId == 999L })
        assertEquals(0, accountStore.consecutiveFailureCount(localIndex = null, countryCode = "ua"))
    }

    @Test
    fun `a failed authorization results in exactly one token-endpoint request`() = runBlocking {
        var tokenEndpointRequests = 0
        val engine = MockEngine { request ->
            if (request.url.toString().contains("/open/oauth/token")) {
                tokenEndpointRequests += 1
                respond(
                    content = """{"error":"invalid_grant","error_description":"bad code"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders(),
                )
            } else {
                error("Unexpected request: ${request.url}")
            }
        }
        val harness = harness(engine, OlxAccountStore(InMemoryOlxKeyValueStore(), testJson))

        val outcome = completeAddAccount(harness)

        assertIs<AddAccountOutcome.Failed>(outcome)
        assertEquals(1, tokenEndpointRequests)
    }

    @Test
    fun `disconnectAccount promotes the most recently used remaining account and clears the bearer cache`() = runBlocking {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(
                    account(localIndex = 1, olxUserId = 1L, accessToken = "token-a", lastUsedAtEpochSeconds = 100),
                    account(localIndex = 2, olxUserId = 2L, accessToken = "token-b", lastUsedAtEpochSeconds = 300),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 3,
            ),
        )
        var lastAuthorizationHeader: String? = null
        val engine = MockEngine { request ->
            lastAuthorizationHeader = request.headers[HttpHeaders.Authorization]
            respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val harness = harness(engine, accountStore)
        runCatching { harness.olxApiClient.getAuthenticatedUser() }
        assertEquals("Bearer token-a", lastAuthorizationHeader)

        harness.repository.disconnectAccount("ua", 1)

        val record = harness.accountStore.readRaw()!!
        assertEquals(2, record.activeByCountry["ua"])
        runCatching { harness.olxApiClient.getAuthenticatedUser() }
        assertEquals("Bearer token-b", lastAuthorizationHeader)
        assertTrue(harness.analytics.events.any { it.first == AnalyticsEvents.ACCOUNT_DISCONNECTED })
    }

    @Test
    fun `runKeepAliveRefresh refreshes a 21-day-stale account and skips a 19-day-stale and a NeedsReconnect one`() = runBlocking {
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        val staleUsable = account(
            localIndex = 1,
            countryCode = "ua",
            olxUserId = 1L,
            accessToken = "ua-old-token",
            refreshToken = "ua-refresh-token",
            lastRefreshedAtEpochSeconds = now - 21 * 86_400,
        )
        val freshUsable = account(
            localIndex = 2,
            countryCode = "pl",
            olxUserId = 2L,
            accessToken = "pl-old-token",
            refreshToken = "pl-refresh-token",
            lastRefreshedAtEpochSeconds = now - 19 * 86_400,
        )
        val needsReconnect = account(
            localIndex = 3,
            countryCode = "bg",
            olxUserId = 3L,
            accessToken = "bg-old-token",
            refreshToken = "bg-refresh-token",
            lastRefreshedAtEpochSeconds = now - 25 * 86_400,
            state = OlxAccountState.NeedsReconnect,
        )
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(staleUsable, freshUsable, needsReconnect),
                activeByCountry = mapOf("ua" to 1, "pl" to 2, "bg" to 3),
                nextLocalIndex = 4,
            ),
        )
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.startsWith("https://www.olx.ua/") -> respond(
                    content = """
                        {
                          "access_token": "ua-fresh-token",
                          "refresh_token": "ua-fresh-refresh-token",
                          "expires_in": 86400,
                          "token_type": "bearer",
                          "scope": "v2 read write"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )

                else -> error("Unexpected keep-alive request to a non-stale/NeedsReconnect account: $url")
            }
        }
        val harness = harness(engine, accountStore)

        harness.repository.runKeepAliveRefresh()

        val record = harness.accountStore.readRaw()!!
        assertEquals("ua-fresh-token", record.accounts.single { it.localIndex == 1 }.tokens.accessToken)
        assertEquals("pl-old-token", record.accounts.single { it.localIndex == 2 }.tokens.accessToken)
        assertEquals("bg-old-token", record.accounts.single { it.localIndex == 3 }.tokens.accessToken)
        assertEquals(OlxAccountState.NeedsReconnect, record.accounts.single { it.localIndex == 3 }.state)
    }

    @Test
    fun `runKeepAliveRefresh marks NeedsReconnect and fires the expired-unused event on invalid_grant`() = runBlocking {
        val now = Clock.System.now().toEpochMilliseconds() / 1000
        val staleUsable = account(
            localIndex = 1,
            countryCode = "ua",
            olxUserId = 1L,
            accessToken = "ua-old-token",
            refreshToken = "ua-refresh-token",
            lastUsedAtEpochSeconds = now - 30 * 86_400,
            lastRefreshedAtEpochSeconds = now - 30 * 86_400,
        )
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(staleUsable),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_grant","error_description":"refresh token expired"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders(),
            )
        }
        val harness = harness(engine, accountStore)

        harness.repository.runKeepAliveRefresh()

        val record = harness.accountStore.readRaw()!!
        assertEquals(OlxAccountState.NeedsReconnect, record.accounts.single().state)
        val expiredEvent = harness.analytics.events.single { it.first == AnalyticsEvents.ACCOUNT_TOKEN_EXPIRED_UNUSED }
        assertEquals(30L, expiredEvent.second["days_since_last_use"])
    }

    // --- test harness -----------------------------------------------------------------------

    private suspend fun completeAddAccount(harness: TestHarness): AddAccountOutcome {
        val request = harness.repository.createAuthorizationRequest()
        return harness.repository.addAccount("${request.redirectUri}?code=one-time-code&state=${request.state}")
    }

    private fun tokenAndProfileEngine(profileOlxUserId: Long): MockEngine = MockEngine { request ->
        val url = request.url.toString()
        when {
            url.contains("/open/oauth/token") -> respond(
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
                headers = jsonHeaders(),
            )

            url.contains("/partner/users/me") -> respond(
                content = """{"data":{"id":$profileOlxUserId,"email":"seller@example.com","name":"Seller"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )

            else -> error("Unexpected request: $url")
        }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun account(
        localIndex: Int,
        olxUserId: Long,
        accessToken: String,
        refreshToken: String = "refresh-token",
        countryCode: String = "ua",
        lastUsedAtEpochSeconds: Long = 0,
        lastRefreshedAtEpochSeconds: Long = 0,
        state: OlxAccountState = OlxAccountState.Usable,
        profile: OlxProfileSnapshot? = null,
    ) = OlxAccountRecord(
        localIndex = localIndex,
        countryCode = countryCode,
        olxUserId = olxUserId,
        tokens = OlxTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = 86_400,
            tokenType = "bearer",
            scope = "v2 read write",
            issuedAtEpochSeconds = 0,
        ),
        lastUsedAtEpochSeconds = lastUsedAtEpochSeconds,
        lastRefreshedAtEpochSeconds = lastRefreshedAtEpochSeconds,
        state = state,
        profile = profile,
    )

    private suspend fun harness(engine: MockEngine, accountStore: OlxAccountStore): TestHarness {
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore()).apply { save(OlxCountry.UA) }
        val errorParser = OlxRemoteErrorParser(testJson)
        val unauthenticatedHttpClient = createOlxHttpClient(engine)
        val authorizedHttpClient = createOlxAuthorizedHttpClient(
            authRefreshClient = unauthenticatedHttpClient,
            credentialsProvider = TestCredentialsProvider(),
            accountStore = accountStore,
            countryStore = countryStore,
            errorParser = errorParser,
            engine = engine,
        )
        val olxApiClient = OlxApiClient(httpClient = authorizedHttpClient, json = testJson, errorParser = errorParser)
        val unauthenticatedOlxApiClient = OlxApiClient(httpClient = unauthenticatedHttpClient, json = testJson, errorParser = errorParser)
        val authRepository = OlxAuthRepository(
            httpClient = unauthenticatedHttpClient,
            credentialsProvider = TestCredentialsProvider(),
            accountStore = accountStore,
            countryStore = countryStore,
            authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
            redirectHandler = TestRedirectHandler(),
            guestModeStore = GuestModeStore(InMemoryOlxKeyValueStore()),
            errorParser = errorParser,
        )
        val analytics = FakeAnalytics()
        val analyticsConsentRepository = AnalyticsConsentRepository(
            store = AnalyticsConsentStore(InMemoryOlxKeyValueStore()),
            analytics = analytics,
            applicationScope = CoroutineScope(Dispatchers.Default),
        )
        val locationRepository = LocationRepository(
            locationProvider = object : LocationProvider {
                override suspend fun getCurrentLocation(): DeviceLocation? = null
            },
            olxApiClient = olxApiClient,
            locationStore = LocationStore(InMemoryOlxKeyValueStore(), testJson),
        )
        val repository = SellerAccountRepository(
            authRepository = authRepository,
            olxApiClient = olxApiClient,
            unauthenticatedOlxApiClient = unauthenticatedOlxApiClient,
            authorizedHttpClient = authorizedHttpClient,
            unauthenticatedHttpClient = unauthenticatedHttpClient,
            accountStore = accountStore,
            locationRepository = locationRepository,
            olxCountryStore = countryStore,
            draftMediaFileStore = FakeDraftMediaFileStore,
            analyticsConsentRepository = analyticsConsentRepository,
            errorParser = errorParser,
            analytics = analytics,
        )
        return TestHarness(repository, accountStore, olxApiClient, analytics)
    }

    private data class TestHarness(
        val repository: SellerAccountRepository,
        val accountStore: OlxAccountStore,
        val olxApiClient: OlxApiClient,
        val analytics: FakeAnalytics,
    )

    private class TestCredentialsProvider : OlxCredentialsProvider {
        override suspend fun getClientId(): String = "test-client-id"
        override suspend fun getClientSecret(): String = "test-client-secret"
    }

    private class TestRedirectHandler : OlxRedirectHandler {
        override fun buildRedirectUri(platform: com.sirelon.sellsnap.platform.PlatformTargets): String =
            "selolxai://olx-auth/callback"

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

    private class FakeAnalytics : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val userProperties = mutableMapOf<String, String?>()
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {
            userProperties[name] = value
        }
        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }

    private object FakeDraftMediaFileStore : DraftMediaFileStore {
        override suspend fun persist(file: KmpFile): PersistedDraftPhoto? = null
        override fun restore(photo: DraftPhoto): KmpFile? = null
        override fun stablePath(file: KmpFile): String? = null
        override suspend fun delete(photos: List<DraftPhoto>) {}
        override suspend fun deleteAll() {}
    }
}
