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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Covers TRD A4 (SIR-83): an account-scoped load in flight when the active account switches must
 * be discarded rather than rendered under the (now stale) header. Everything - the ViewModel's own
 * `viewModelScope` coroutines and the mock HTTP engine's dispatch - is pinned to the same
 * [StandardTestDispatcher] so the test can deterministically control exactly when the "in-flight"
 * request resolves relative to the account switch, with no real threads or timing races involved.
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
    fun `an in-flight load whose epoch changes before it returns does not update state adverts`() = runTest(testDispatcher) {
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

        var requestCount = 0
        val staleRequestGate = CompletableDeferred<Unit>()
        val engineConfig = MockEngineConfig().apply {
            addHandler { _ ->
                requestCount += 1
                if (requestCount == 1) {
                    // The first refresh's request never resolves until the test releases it below -
                    // this is the "in-flight load" whose account switches out from underneath it.
                    staleRequestGate.await()
                    respond(advertsJson(id = 111L), status = HttpStatusCode.OK, headers = jsonHeaders())
                } else {
                    respond(advertsJson(id = 999L), status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        // Pin the mock engine's own dispatch to the test dispatcher too, so the HTTP round trip
        // stays on the same deterministic scheduler as the ViewModel's viewModelScope coroutines.
        engineConfig.dispatcher = testDispatcher
        val engine = MockEngine(engineConfig)

        val harness = harness(engine, accountStore)
        val viewModel = MyAdvertsViewModel(MyAdvertsRepository(harness.olxApiClient), harness.repository)

        // runCurrent() (not advanceUntilIdle()) deliberately: the shared OLX client installs a
        // request-timeout plugin that schedules its own delay()-based timer, and advanceUntilIdle()
        // would fast-forward virtual time straight through it once nothing else is runnable,
        // failing the "in-flight" request before the test gets a chance to race the account switch
        // against it. runCurrent() only drains work that's ready *now*, never skips time forward.

        // Let the ViewModel's init-time refresh() start and suspend on the first (stale-to-be) request.
        runCurrent()
        assertEquals(true, viewModel.state.value.isLoading)

        // Switching accounts bumps switchEpoch, which reactively triggers a second refresh() for
        // the newly active account - its request resolves immediately with distinct data.
        harness.repository.setActiveAccount("ua", 2)
        runCurrent()

        assertEquals(listOf(999L), viewModel.state.value.adverts.map { it.id })
        assertFalse(viewModel.state.value.isLoading)

        // Now let the stale first request finally resolve. Its epoch snapshot no longer matches,
        // so it must be discarded rather than clobbering the fresh result above.
        staleRequestGate.complete(Unit)
        runCurrent()

        assertEquals(listOf(999L), viewModel.state.value.adverts.map { it.id })
        assertFalse(viewModel.state.value.isLoading)
    }

    // --- test harness -----------------------------------------------------------------------

    private fun advertsJson(id: Long) = """{"data":[{"id":$id,"status":"active"}]}"""

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun account(
        localIndex: Int,
        olxUserId: Long,
        accessToken: String,
        countryCode: String = "ua",
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
            issuedAtEpochSeconds = 0,
        ),
        lastUsedAtEpochSeconds = 0,
        lastRefreshedAtEpochSeconds = 0,
        state = OlxAccountState.Usable,
        profile = null,
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
        return TestHarness(repository, olxApiClient)
    }

    private data class TestHarness(
        val repository: SellerAccountRepository,
        val olxApiClient: OlxApiClient,
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
