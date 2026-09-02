package com.sirelon.sellsnap.features.seller.my_ads.presentation

import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.media.upload.DraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import com.sirelon.sellsnap.features.media.upload.PersistedDraftPhoto
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxConfig
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.features.seller.location.DeviceLocation
import com.sirelon.sellsnap.features.seller.location.LocationProvider
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.location.data.LocationStore
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Covers the SIR-87 account pager: each [MyAdvertsContract.AccountPage] loads, pages, errors and
 * retries independently, addressed by its own explicit-token fetch rather than the shared
 * authorized client (see MyAdvertsRepository/SellerAccountRepository.accessTokenFor). Everything -
 * the ViewModel's own `viewModelScope` coroutines and the mock HTTP engine's dispatch - is pinned
 * to the same [StandardTestDispatcher] so tests can deterministically control exactly when a
 * request resolves relative to another ViewModel event, with no real threads or timing races
 * involved.
 */
class MyAdvertsViewModelTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `two connected accounts produce two stable pages with the active account preselected and loaded`() = runTest(testDispatcher) {
        val requestLog = mutableListOf<LoggedRequest>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                requestLog += request.toLogged()
                respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf(1, 2), state.pages.map { it.localIndex })
        assertEquals(1, state.selectedLocalIndex)
        assertTrue(state.pages.first { it.localIndex == 1 }.isActiveAccount)
        assertEquals(false, state.pages.first { it.localIndex == 2 }.isActiveAccount)

        // Only the initially-selected page (the active account) fetches on screen entry - never
        // all connected accounts at once.
        assertEquals(1, requestLog.size)
        assertEquals("Bearer token-1", requestLog.single().authHeader)
    }

    @Test
    fun `selecting a second page fetches with that account's own token and leaves the first page untouched`() = runTest(testDispatcher) {
        val requestLog = mutableListOf<LoggedRequest>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                requestLog += request.toLogged()
                val id = if (request.headers[HttpHeaders.Authorization] == "Bearer token-2") 222L else 111L
                respond(advertsJson(id), status = HttpStatusCode.OK, headers = jsonHeaders())
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()
        val page1AdvertsBeforeSwitch = viewModel.state.value.pages.first { it.localIndex == 1 }.adverts

        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.selectedLocalIndex)
        assertEquals(listOf(222L), state.pages.first { it.localIndex == 2 }.adverts.map { it.id })
        assertEquals(page1AdvertsBeforeSwitch, state.pages.first { it.localIndex == 1 }.adverts)
        assertEquals("Bearer token-2", requestLog.last().authHeader)
    }

    @Test
    fun `a failure on one page does not affect another page's state`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.headers[HttpHeaders.Authorization] == "Bearer token-2") {
                    respond("", status = HttpStatusCode.InternalServerError)
                } else {
                    respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()

        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        // The failure branch resolves an error string via getString(), whose loader runs on
        // Compose Resources' own AsyncCache scope (real Dispatchers.Default, not this virtual
        // test dispatcher - see ResourceCaches.kt) - advanceUntilIdle() alone can't observe it.
        awaitPageSettled(viewModel, localIndex = 2)

        val page1 = viewModel.state.value.pages.first { it.localIndex == 1 }
        val page2 = viewModel.state.value.pages.first { it.localIndex == 2 }
        assertNull(page1.errorMessage)
        assertEquals(listOf(111L), page1.adverts.map { it.id })
        assertNotNull(page2.errorMessage)
        assertEquals(emptyList(), page2.adverts)
    }

    @Test
    fun `an InvalidToken response triggers exactly one forced refresh and the retry uses the refreshed token`() = runTest(testDispatcher) {
        var advertsCallCount = 0
        var refreshCallCount = 0
        val advertsAuthHeaders = mutableListOf<String?>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.contains(OlxConfig.authTokenPath)) {
                    refreshCallCount += 1
                    respond(refreshTokenJson("token-1-refreshed"), status = HttpStatusCode.OK, headers = jsonHeaders())
                } else {
                    advertsCallCount += 1
                    advertsAuthHeaders += request.headers[HttpHeaders.Authorization]
                    if (advertsCallCount == 1) {
                        respond("", status = HttpStatusCode.Unauthorized)
                    } else {
                        respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                    }
                }
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(account(localIndex = 1, olxUserId = 1L, accessToken = "token-1")),
            activeIndex = 1,
        )
        runCurrent()

        assertEquals(1, refreshCallCount)
        assertEquals(2, advertsCallCount)
        assertEquals(listOf<String?>("Bearer token-1", "Bearer token-1-refreshed"), advertsAuthHeaders)
        assertEquals(listOf(111L), viewModel.state.value.pages.single().adverts.map { it.id })
    }

    @Test
    fun `a terminal invalid_grant refresh failure marks only that account NeedsReconnect`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.url.encodedPath.contains(OlxConfig.authTokenPath)) {
                    respond("""{"error":"invalid_grant"}""", status = HttpStatusCode.BadRequest, headers = jsonHeaders())
                } else {
                    respond(advertsJson(222L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }

        val (viewModel, _) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                // Already expired, so the first accessTokenFor call refreshes proactively and
                // hits the invalid_grant response above - no need to manufacture a prior 401.
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1", issuedAtEpochSeconds = 0),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()

        val page1 = viewModel.state.value.pages.first { it.localIndex == 1 }
        assertTrue(page1.needsReconnect)
        assertEquals(false, page1.isLoading)
        assertEquals(emptyList(), page1.adverts)

        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        runCurrent()

        val page2 = viewModel.state.value.pages.first { it.localIndex == 2 }
        assertEquals(false, page2.needsReconnect)
        assertEquals(listOf(222L), page2.adverts.map { it.id })
    }

    @Test
    fun `an external active-account switch moves the isActiveAccount badge without changing the selected page`() = runTest(testDispatcher) {
        val engine = mockEngine(testDispatcher) {
            addHandler { respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders()) }
        }

        val (viewModel, harness) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()
        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        runCurrent()

        harness.repository.setActiveAccount("ua", 2)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.selectedLocalIndex)
        assertTrue(state.pages.first { it.localIndex == 2 }.isActiveAccount)
        assertEquals(false, state.pages.first { it.localIndex == 1 }.isActiveAccount)
    }

    @Test
    fun `disconnecting the selected account drops its page and any in-flight result for it`() = runTest(testDispatcher) {
        val staleRequestGate = CompletableDeferred<Unit>()
        val engine = mockEngine(testDispatcher) {
            addHandler { request ->
                if (request.headers[HttpHeaders.Authorization] == "Bearer token-2") {
                    // Never resolves until the test releases it below - the "in-flight" load for
                    // the page that gets disconnected out from underneath it.
                    staleRequestGate.await()
                    respond(advertsJson(222L), status = HttpStatusCode.OK, headers = jsonHeaders())
                } else {
                    respond(advertsJson(111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }

        val (viewModel, harness) = setUpViewModel(
            engine = engine,
            accounts = listOf(
                account(localIndex = 1, olxUserId = 1L, accessToken = "token-1"),
                account(localIndex = 2, olxUserId = 2L, accessToken = "token-2"),
            ),
            activeIndex = 1,
        )
        runCurrent()

        viewModel.onEvent(MyAdvertsContract.Event.PageSelected(2))
        runCurrent()
        assertEquals(2, viewModel.state.value.selectedLocalIndex)

        harness.repository.disconnectAccount("ua", 2)
        runCurrent()

        val stateAfterDisconnect = viewModel.state.value
        assertEquals(listOf(1), stateAfterDisconnect.pages.map { it.localIndex })
        assertEquals(1, stateAfterDisconnect.selectedLocalIndex)

        // Let the stale request for the now-vanished page 2 finally resolve - it must be dropped
        // (updatePage is a no-op for a localIndex that no longer exists) rather than resurrecting it.
        staleRequestGate.complete(Unit)
        runCurrent()

        assertEquals(listOf(1), viewModel.state.value.pages.map { it.localIndex })
    }

    // --- test harness -----------------------------------------------------------------------

    /**
     * A failed page load resolves its error message via `getString`, which Compose Resources
     * loads through its own `AsyncCache` background scope - a real `Dispatchers.Default` job
     * entirely outside this test's virtual [StandardTestDispatcher], so [runCurrent] can never
     * observe its completion. Polls with a genuine (real-time) short delay on [Dispatchers.Default]
     * between attempts so that background job actually gets to run, bounded so a real regression
     * still fails the test instead of hanging.
     */
    private suspend fun TestScope.awaitPageSettled(viewModel: MyAdvertsViewModel, localIndex: Int, maxAttempts: Int = 200) {
        repeat(maxAttempts) {
            runCurrent()
            val page = viewModel.state.value.pages.find { it.localIndex == localIndex }
            if (page != null && !page.isLoading && !page.isLoadingMore) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        error("Page $localIndex did not settle in time")
    }

    private data class LoggedRequest(val path: String, val authHeader: String?)

    private fun io.ktor.client.request.HttpRequestData.toLogged() =
        LoggedRequest(url.encodedPath, headers[HttpHeaders.Authorization])

    private fun mockEngine(dispatcher: CoroutineDispatcher, configure: MockEngineConfig.() -> Unit): MockEngine {
        val config = MockEngineConfig().apply(configure)
        config.dispatcher = dispatcher
        return MockEngine(config)
    }

    private fun advertsJson(id: Long) = """{"data":[{"id":$id,"status":"active"}]}"""

    private fun refreshTokenJson(accessToken: String) =
        """{"access_token":"$accessToken","refresh_token":"refresh-new","expires_in":86400,"token_type":"bearer","scope":"v2 read write"}"""

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun nowEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

    private fun account(
        localIndex: Int,
        olxUserId: Long,
        accessToken: String,
        countryCode: String = "ua",
        // Real wall-clock issue time by default so accessTokenFor's expiry check treats the
        // token as fresh - tests that want an already-expired token (to force a proactive
        // refresh) pass issuedAtEpochSeconds = 0 explicitly.
        issuedAtEpochSeconds: Long = nowEpochSeconds(),
    ) = OlxAccountRecord(
        localIndex = localIndex,
        countryCode = countryCode,
        olxUserId = olxUserId,
        tokens = OlxTokens(
            accessToken = accessToken,
            refreshToken = "refresh-token-$localIndex",
            expiresInSeconds = 86_400,
            tokenType = "bearer",
            scope = "v2 read write",
            issuedAtEpochSeconds = issuedAtEpochSeconds,
        ),
        lastUsedAtEpochSeconds = 0,
        lastRefreshedAtEpochSeconds = 0,
        state = OlxAccountState.Usable,
        profile = null,
    )

    private suspend fun setUpViewModel(
        engine: MockEngine,
        accounts: List<OlxAccountRecord>,
        activeIndex: Int,
        countryCode: String = "ua",
    ): Pair<MyAdvertsViewModel, TestHarness> {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = accounts,
                activeByCountry = mapOf(countryCode to activeIndex),
                nextLocalIndex = (accounts.maxOfOrNull { it.localIndex } ?: 0) + 1,
            ),
        )
        val harness = harness(engine, accountStore)
        val repository = MyAdvertsRepository(
            accountRepository = harness.repository,
            unauthenticatedOlxApiClient = harness.unauthenticatedOlxApiClient,
        )
        val viewModel = MyAdvertsViewModel(repository, harness.repository)
        return viewModel to harness
    }

    private suspend fun harness(engine: MockEngine, accountStore: OlxAccountStore): TestHarness {
        val analytics = FakeAnalytics()
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), analytics).apply { save(OlxCountry.UA) }
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
        return TestHarness(repository, olxApiClient, unauthenticatedOlxApiClient)
    }

    private data class TestHarness(
        val repository: SellerAccountRepository,
        val olxApiClient: OlxApiClient,
        val unauthenticatedOlxApiClient: OlxApiClient,
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
        override fun logEvent(name: String, params: Map<String, Any>) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
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
