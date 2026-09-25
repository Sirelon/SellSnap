package com.sirelon.sellsnap.features.auth.presentation

import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
import com.sirelon.sellsnap.features.media.upload.DraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import com.sirelon.sellsnap.features.media.upload.PersistedDraftPhoto
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
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
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerAuthContract
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerAuthViewModel
import com.sirelon.sellsnap.features.seller.location.DeviceLocation
import com.sirelon.sellsnap.features.seller.location.LocationProvider
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.location.data.LocationStore
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentStore
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SellerAuthViewModelTest {

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
    fun `closing the OLX login logs auth_abandoned and offers guest mode without entering it`() =
        runTest(testDispatcher) {
            val harness = harness(engine = MockEngine { error("No HTTP call expected.") })

            harness.viewModel.onEvent(SellerAuthContract.SellerAuthEvent.OlxAuthDismissed)

            assertTrue(harness.analytics.events.any { it.first == AnalyticsEvents.AUTH_ABANDONED })
            assertTrue(harness.viewModel.state.value.showLoginClosedSheet)
            assertEquals(SellerAuthContract.SellerAuthStatus.Idle, harness.viewModel.state.value.status)
            assertEquals(SellerSessionMode.Unauthenticated, harness.authRepository.currentSession().mode)
        }

    @Test
    fun `choosing guest mode on the login-closed sheet enters it with a connect-later hint and opens home`() =
        runTest(testDispatcher) {
            val harness = harness(engine = MockEngine { error("No HTTP call expected.") })

            harness.viewModel.onEvent(SellerAuthContract.SellerAuthEvent.OlxAuthDismissed)
            harness.viewModel.onEvent(SellerAuthContract.SellerAuthEvent.LoginClosedGuestChosen)
            harness.viewModel.effects.awaitEffect<SellerAuthContract.SellerAuthEffect.OpenHome>()

            assertFalse(harness.viewModel.state.value.showLoginClosedSheet)
            assertEquals(SellerSessionMode.Guest, harness.authRepository.currentSession().mode)
            assertTrue(harness.authRepository.consumeGuestConnectHint())
        }

    @Test
    fun `dismissing the login-closed sheet stays put, not in guest mode`() = runTest(testDispatcher) {
        val harness = harness(engine = MockEngine { error("No HTTP call expected.") })

        harness.viewModel.onEvent(SellerAuthContract.SellerAuthEvent.OlxAuthDismissed)
        harness.viewModel.onEvent(SellerAuthContract.SellerAuthEvent.LoginClosedSheetDismissed)

        assertFalse(harness.viewModel.state.value.showLoginClosedSheet)
        assertEquals(SellerSessionMode.Unauthenticated, harness.authRepository.currentSession().mode)
        assertFalse(harness.authRepository.consumeGuestConnectHint())
    }

    @Test
    fun `continuing as guest does not set the connect-later hint`() = runTest(testDispatcher) {
        val harness = harness(engine = MockEngine { error("No HTTP call expected.") })

        harness.viewModel.onEvent(SellerAuthContract.SellerAuthEvent.ContinueAsGuestClicked)
        harness.viewModel.effects.awaitEffect<SellerAuthContract.SellerAuthEffect.OpenHome>()

        assertEquals(SellerSessionMode.Guest, harness.authRepository.currentSession().mode)
        assertFalse(harness.authRepository.consumeGuestConnectHint())
        assertFalse(harness.analytics.events.any { it.first == AnalyticsEvents.AUTH_ABANDONED })
    }

    private suspend fun harness(engine: MockEngine): TestHarness {
        val analytics = FakeAnalytics()
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), analytics).apply { save(OlxCountry.UA) }
        val errorParser = OlxRemoteErrorParser(testJson)
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
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
        val accountRepository = SellerAccountRepository(
            authRepository = authRepository,
            olxApiClient = olxApiClient,
            unauthenticatedOlxApiClient = unauthenticatedOlxApiClient,
            authorizedHttpClient = authorizedHttpClient,
            unauthenticatedHttpClient = unauthenticatedHttpClient,
            accountStore = accountStore,
            locationRepository = locationRepository,
            olxCountryStore = countryStore,
            draftMediaFileStore = FakeDraftMediaFileStore,
            advertOutcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson),
            analyticsConsentRepository = analyticsConsentRepository,
            errorParser = errorParser,
            analytics = analytics,
        )
        val viewModel = SellerAuthViewModel(
            authRepository = authRepository,
            accountRepository = accountRepository,
            analytics = analytics,
            olxCountryStore = countryStore,
        )
        return TestHarness(viewModel, authRepository, analytics)
    }

    private data class TestHarness(
        val viewModel: SellerAuthViewModel,
        val authRepository: OlxAuthRepository,
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
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }
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
