package com.sirelon.sellsnap.features.seller.ad.preview_ad

import androidx.lifecycle.SavedStateHandle
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.media.upload.DraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import com.sirelon.sellsnap.features.media.upload.PersistedDraftPhoto
import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.ad.generation_log.NoOpAdGenerationLogRepository
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEffect
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdContract.PreviewAdEvent
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
import com.sirelon.sellsnap.features.seller.auth.data.OlxProfileSnapshot
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.features.seller.categories.data.CategoriesRepository
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeValidator
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.currency.data.CurrencyRepository
import com.sirelon.sellsnap.features.seller.location.DeviceLocation
import com.sirelon.sellsnap.features.seller.location.LocationProvider
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.openai.OpenAIClient
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.sirelon.sellsnap.features.seller.location.data.LocationStore
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * SIR-83 D6/A5: the identity assertion before publish is the primary defence against publishing
 * a listing to the wrong OLX account (PRD G4 - "must be zero in the wild"). Everything here -
 * the ViewModel's viewModelScope coroutines and the mock HTTP engine's dispatch - is pinned to
 * the same [StandardTestDispatcher], matching MyAdvertsViewModelTest's pattern, so an id mismatch
 * or a switch mid-flight can be asserted deterministically rather than raced.
 */
class PreviewAdViewModelTest {

    private val testJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val testDispatcher = StandardTestDispatcher()
    private val testCategory = OlxCategory(id = 1, label = "Electronics", parentId = null, isLeaf = true)
    private val testLocation = OlxLocation(cityId = 1, cityName = "Kyiv", districtId = null, districtName = null)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `publish aborts before POST when the fetched user id does not match the active account`() = runTest(testDispatcher) {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(account(localIndex = 1, olxUserId = 100L, accessToken = "token-a")),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )
        var postAdvertRequests = 0
        val engine = buildEngine {
            addHandler { request ->
                when {
                    request.url.encodedPath.contains("users/me") ->
                        respond(userJson(id = 999L, name = "Wrong User"), status = HttpStatusCode.OK, headers = jsonHeaders())

                    request.url.encodedPath.contains("adverts") && request.method == HttpMethod.Post -> {
                        postAdvertRequests += 1
                        respond(postAdvertJson(id = 1L), status = HttpStatusCode.OK, headers = jsonHeaders())
                    }

                    else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val harness = harness(engine, accountStore)
        val viewModel = buildViewModel(harness)
        viewModel.setState { it.copy(selectedCategory = testCategory, location = testLocation) }
        val effects = mutableListOf<PreviewAdEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }

        viewModel.onEvent(PreviewAdEvent.Publish)
        advanceUntilIdle()

        assertEquals(0, postAdvertRequests, "an id mismatch must abort before any POST adverts request")
        assertTrue(harness.analytics.events.any { it.first == AnalyticsEvents.PUBLISH_ACCOUNT_MISMATCH_ABORTED })
        assertTrue(effects.any { it is PreviewAdEffect.PublishAccountMismatch })
    }

    @Test
    fun `publish does not abort when the active account has no olxUserId yet`() = runTest(testDispatcher) {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                // The migrated legacy account, before its first successful users/me (TRD F5/D6).
                accounts = listOf(account(localIndex = 1, olxUserId = null, accessToken = "token-a", profile = null)),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )
        var postAdvertRequests = 0
        val engine = buildEngine {
            addHandler { request ->
                when {
                    request.url.encodedPath.contains("users/me") ->
                        respond(userJson(id = 555L, name = "Legacy Seller"), status = HttpStatusCode.OK, headers = jsonHeaders())

                    request.url.encodedPath.contains("adverts") && request.method == HttpMethod.Post -> {
                        postAdvertRequests += 1
                        respond(postAdvertJson(id = 2L), status = HttpStatusCode.OK, headers = jsonHeaders())
                    }

                    else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val harness = harness(engine, accountStore)
        val viewModel = buildViewModel(harness)
        viewModel.setState { it.copy(selectedCategory = testCategory, location = testLocation) }
        val effects = mutableListOf<PreviewAdEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }

        viewModel.onEvent(PreviewAdEvent.Publish)
        advanceUntilIdle()

        assertEquals(1, postAdvertRequests, "a null olxUserId (not-yet-identified legacy account) must not abort")
        assertFalse(harness.analytics.events.any { it.first == AnalyticsEvents.PUBLISH_ACCOUNT_MISMATCH_ABORTED })
        val success = effects.filterIsInstance<PreviewAdEffect.PublishSuccess>().single()
        assertEquals("Legacy Seller", success.data.accountName)
        val startedEvent = harness.analytics.events.single { it.first == AnalyticsEvents.AD_PUBLISH_STARTED }
        assertEquals(1, startedEvent.second["account_index"])
    }

    @Test
    fun `publish aborts when the active account switches mid-flight even though the fetched id still matches the original target`() =
        runTest(testDispatcher) {
            val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
            accountStore.write(
                OlxAccountsRecord(
                    accounts = listOf(
                        account(localIndex = 1, olxUserId = 100L, accessToken = "token-a"),
                        account(localIndex = 2, olxUserId = 200L, accessToken = "token-b"),
                    ),
                    activeByCountry = mapOf("ua" to 1),
                    nextLocalIndex = 3,
                ),
            )
            var postAdvertRequests = 0
            lateinit var harness: TestHarness
            val engine = buildEngine {
                addHandler { request ->
                    when {
                        request.url.encodedPath.contains("users/me") -> {
                            // Simulate the seller switching accounts elsewhere while this
                            // publish's users/me call is already in flight (TRD "in-flight
                            // switch discard") - the id below still matches the ORIGINAL
                            // target, so only the switchEpoch check catches this.
                            harness.repository.setActiveAccount("ua", 2)
                            respond(userJson(id = 100L, name = "Seller One"), status = HttpStatusCode.OK, headers = jsonHeaders())
                        }

                        request.url.encodedPath.contains("adverts") && request.method == HttpMethod.Post -> {
                            postAdvertRequests += 1
                            respond(postAdvertJson(id = 3L), status = HttpStatusCode.OK, headers = jsonHeaders())
                        }

                        else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
                    }
                }
            }
            harness = harness(engine, accountStore)
            val viewModel = buildViewModel(harness)
            viewModel.setState { it.copy(selectedCategory = testCategory, location = testLocation) }
            val effects = mutableListOf<PreviewAdEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects += it } }

            viewModel.onEvent(PreviewAdEvent.Publish)
            advanceUntilIdle()

            assertEquals(0, postAdvertRequests, "a switch mid-flight must abort even if the id happens to still match")
            assertTrue(harness.analytics.events.any { it.first == AnalyticsEvents.PUBLISH_ACCOUNT_MISMATCH_ABORTED })
            assertTrue(effects.any { it is PreviewAdEffect.PublishAccountMismatch })
        }

    @Test
    fun `publish surfaces a distinct reconnect effect, not the generic failure, when the account token is dead`() = runTest(testDispatcher) {
        val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson)
        accountStore.write(
            OlxAccountsRecord(
                accounts = listOf(
                    account(
                        localIndex = 1,
                        olxUserId = 100L,
                        accessToken = "token-a",
                        profile = OlxProfileSnapshot(name = "Seller One", email = "one@example.com", avatarUrl = null, isBusiness = false),
                    ),
                ),
                activeByCountry = mapOf("ua" to 1),
                nextLocalIndex = 2,
            ),
        )
        val engine = buildEngine {
            addHandler { request ->
                when {
                    request.url.encodedPath.contains("users/me") -> respond(
                        """{"error":"invalid_grant","error_description":"refresh token expired"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = jsonHeaders(),
                    )

                    else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
                }
            }
        }
        val harness = harness(engine, accountStore)
        val viewModel = buildViewModel(harness)
        viewModel.setState { it.copy(selectedCategory = testCategory, location = testLocation) }
        viewModel.onEvent(PreviewAdEvent.Publish)

        // Awaited rather than collected-then-`advanceUntilIdle()`: the failure path resolves its
        // message through compose-resources `getString`, which hops off the test dispatcher, so
        // draining the virtual scheduler does not guarantee the effect has been posted yet. The
        // effects channel is buffered, so awaiting it cannot miss an already-sent one.
        // Awaited rather than drained with `advanceUntilIdle()`: the message is resolved through
        // compose-resources `getString`, which hops off the test dispatcher. No `withTimeout` -
        // inside `runTest` that runs on virtual time and fires the moment the scheduler idles;
        // `runTest`'s own real-time watchdog is what fails this test if nothing arrives.
        //
        // Waits for whichever of the two publish outcomes lands first, so the assertion covers
        // both halves at once - a dead token must produce the named reconnect action, and must
        // not fall through to the generic failure - while tolerating unrelated effects on the way.
        val outcome = viewModel.effects.first {
            it is PreviewAdEffect.PublishNeedsReconnect || it is PreviewAdEffect.PublishFailure
        }

        val reconnect = assertIs<PreviewAdEffect.PublishNeedsReconnect>(outcome)
        assertTrue(reconnect.message.contains("Seller One"))
        assertTrue(reconnect.actionLabel.contains("Seller One"))
    }

    // --- test harness -----------------------------------------------------------------------

    private fun buildEngine(block: MockEngineConfig.() -> Unit): MockEngine {
        val config = MockEngineConfig().apply(block)
        config.dispatcher = testDispatcher
        return MockEngine(config)
    }

    private fun userJson(id: Long, name: String) =
        """{"data":{"id":$id,"email":"seller@example.com","name":"$name"}}"""

    private fun postAdvertJson(id: Long) =
        """{"data":{"id":$id,"status":"new","url":"https://www.olx.ua/d/obyavlenie/test-ID$id.html"}}"""

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun account(
        localIndex: Int,
        olxUserId: Long?,
        accessToken: String,
        countryCode: String = "ua",
        profile: OlxProfileSnapshot? = OlxProfileSnapshot(
            name = "Seller $localIndex",
            email = "seller$localIndex@example.com",
            avatarUrl = null,
            isBusiness = false,
        ),
    ) = OlxAccountRecord(
        localIndex = localIndex,
        countryCode = countryCode,
        olxUserId = olxUserId,
        tokens = OlxTokens(
            accessToken = accessToken,
            refreshToken = "refresh-$localIndex",
            expiresInSeconds = 86_400,
            tokenType = "bearer",
            scope = "v2 read write",
            issuedAtEpochSeconds = 0,
        ),
        lastUsedAtEpochSeconds = 0,
        lastRefreshedAtEpochSeconds = 0,
        state = OlxAccountState.Usable,
        profile = profile,
    )

    private fun buildViewModel(harness: TestHarness): PreviewAdViewModel {
        val advertisement = Advertisement(
            title = "A perfectly fine test title",
            description = "A sufficiently long test description with more than thirty characters in it.",
            images = emptyList(),
            suggestedPrice = 100f,
            minPrice = 50f,
            maxPrice = 200f,
        )
        return PreviewAdViewModel(
            filledAdvertisement = AdvertisementWithAttributes(advertisement, emptyMap()),
            categoriesRepository = harness.categoriesRepository,
            locationRepository = harness.locationRepository,
            olxApiClient = harness.olxApiClient,
            currencyRepository = CurrencyRepository(harness.olxApiClient),
            attributeValidator = AttributeValidator(),
            authRepository = harness.authRepository,
            accountRepository = harness.repository,
            olxCountryStore = harness.countryStore,
            adFlowTimerStore = AdFlowTimerStore(),
            savedStateHandle = SavedStateHandle(),
            json = testJson,
            analytics = harness.analytics,
            openAiClient = OpenAIClient(
                openAI = OpenAI(config = OpenAIConfig(token = "test-token")),
                json = testJson,
                compactJson = testJson,
            ),
            adGenerationLogRepository = NoOpAdGenerationLogRepository,
            advertOutcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson),
        )
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
            advertOutcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), testJson),
            analyticsConsentRepository = analyticsConsentRepository,
            errorParser = errorParser,
            analytics = analytics,
        )
        val categoriesRepository = CategoriesRepository(
            olxApiClient = olxApiClient,
            mapper = CategoriesMapper(),
            scope = CoroutineScope(Dispatchers.Default),
            countryStore = countryStore,
        )
        return TestHarness(repository, olxApiClient, authRepository, countryStore, categoriesRepository, locationRepository, analytics)
    }

    private data class TestHarness(
        val repository: SellerAccountRepository,
        val olxApiClient: OlxApiClient,
        val authRepository: OlxAuthRepository,
        val countryStore: OlxCountryStore,
        val categoriesRepository: CategoriesRepository,
        val locationRepository: LocationRepository,
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
