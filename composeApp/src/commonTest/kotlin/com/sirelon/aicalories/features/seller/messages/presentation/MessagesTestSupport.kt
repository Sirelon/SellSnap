package com.sirelon.sellsnap.features.seller.messages.presentation

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
import com.sirelon.sellsnap.features.seller.drafts.InMemoryDraftsRepository
import com.sirelon.sellsnap.features.seller.location.FakeLocationProvider
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.location.data.LocationStore
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/*
 * Shared setup for the messaging ViewModel tests. Copied from MyAdvertsViewModelTest's harness,
 * which is private to that class. Every id, name and text below is an invented placeholder; the
 * shapes follow the live payloads captured for SIR-88 (extra keys included), none of their values.
 */

internal val messagesTestJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

internal const val OwnUserId = 1L
internal const val BuyerUserId = 900L

internal fun threadJson(id: Long, advertId: Long, interlocutorId: Long = BuyerUserId, unread: Int = 0) =
    """{"id":$id,"advert_id":$advertId,"interlocutor_id":$interlocutorId,"total_count":2,"unread_count":$unread,""" +
        """"created_at":"2026-01-02 10:11:12","is_favourite":false,"uuid":"00000000-0000-0000-0000-00000000000$id"}"""

internal fun threadsJson(vararg threads: String) = """{"data":[${threads.joinToString(",")}]}"""

internal fun messageJson(id: Long, type: String, text: String, attachments: String = "[]") =
    """{"id":$id,"thread_id":5,"created_at":"2026-01-02 10:1$id:00","type":"$type","text":"$text",""" +
        """"is_read":${type == "sent"},"attachments":$attachments,"cvs":[],"phone":"","uuid":"u$id","thread_uuid":"t5"}"""

internal fun messagesJson(vararg messages: String) = """{"data":[${messages.joinToString(",")}]}"""

internal fun advertsJson(vararg ids: Long) = """{"data":[${ids.joinToString(",") { advertJsonBody(it) }}]}"""

internal fun advertJson(id: Long) = """{"data":${advertJsonBody(id)}}"""

private fun advertJsonBody(id: Long) =
    """{"id":$id,"status":"active","title":"Placeholder advert $id","url":"https://www.olx.ua/d/$id",""" +
        """"images":[{"url":"https://example.invalid/$id.jpg"}]}"""

internal fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

internal fun mockEngine(dispatcher: CoroutineDispatcher, configure: MockEngineConfig.() -> Unit): MockEngine {
    val config = MockEngineConfig().apply(configure)
    config.dispatcher = dispatcher
    return MockEngine(config)
}

internal fun testAccount(
    localIndex: Int,
    olxUserId: Long = OwnUserId,
    state: OlxAccountState = OlxAccountState.Usable,
) = OlxAccountRecord(
    localIndex = localIndex,
    countryCode = "ua",
    olxUserId = olxUserId,
    tokens = OlxTokens(
        accessToken = "token-$localIndex",
        refreshToken = "refresh-token-$localIndex",
        expiresInSeconds = 86_400,
        tokenType = "bearer",
        scope = "v2 read write",
        issuedAtEpochSeconds = Clock.System.now().toEpochMilliseconds() / 1000,
    ),
    lastUsedAtEpochSeconds = 0,
    lastRefreshedAtEpochSeconds = 0,
    state = state,
    profile = null,
)

internal class MessagesHarness(
    val accountRepository: SellerAccountRepository,
    val accountStore: OlxAccountStore,
    val messagesRepository: MessagesRepository,
    val myAdvertsRepository: MyAdvertsRepository,
    val analytics: FakeAnalytics,
)

internal suspend fun messagesHarness(
    engine: MockEngine,
    accounts: List<OlxAccountRecord>,
    activeIndex: Int?,
): MessagesHarness {
    val accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), messagesTestJson)
    accountStore.write(
        OlxAccountsRecord(
            accounts = accounts,
            activeByCountry = if (activeIndex == null) emptyMap() else mapOf("ua" to activeIndex),
            nextLocalIndex = (accounts.maxOfOrNull { it.localIndex } ?: 0) + 1,
        ),
    )
    val analytics = FakeAnalytics()
    val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), analytics).apply { save(OlxCountry.UA) }
    val errorParser = OlxRemoteErrorParser(messagesTestJson)
    val unauthenticatedHttpClient = createOlxHttpClient(engine)
    val authorizedHttpClient = createOlxAuthorizedHttpClient(
        authRefreshClient = unauthenticatedHttpClient,
        credentialsProvider = TestCredentialsProvider(),
        accountStore = accountStore,
        countryStore = countryStore,
        errorParser = errorParser,
        engine = engine,
    )
    val olxApiClient = OlxApiClient(httpClient = authorizedHttpClient, json = messagesTestJson, errorParser = errorParser)
    val unauthenticatedOlxApiClient =
        OlxApiClient(httpClient = unauthenticatedHttpClient, json = messagesTestJson, errorParser = errorParser)
    val authRepository = OlxAuthRepository(
        httpClient = unauthenticatedHttpClient,
        credentialsProvider = TestCredentialsProvider(),
        accountStore = accountStore,
        countryStore = countryStore,
        authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), messagesTestJson),
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
        locationProvider = FakeLocationProvider(),
        olxApiClient = olxApiClient,
        locationStore = LocationStore(InMemoryOlxKeyValueStore(), messagesTestJson),
        countryStore = countryStore,
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
        advertOutcomeStore = AdvertOutcomeStore(InMemoryOlxKeyValueStore(), messagesTestJson),
        draftsRepository = InMemoryDraftsRepository(),
        analyticsConsentRepository = analyticsConsentRepository,
        errorParser = errorParser,
        analytics = analytics,
    )
    return MessagesHarness(
        accountRepository = accountRepository,
        accountStore = accountStore,
        messagesRepository = MessagesRepository(accountRepository, unauthenticatedOlxApiClient),
        myAdvertsRepository = MyAdvertsRepository(accountRepository, unauthenticatedOlxApiClient),
        analytics = analytics,
    )
}

internal class FakeAnalytics : Analytics {
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

private object FakeDraftMediaFileStore : DraftMediaFileStore {
    override suspend fun persist(file: KmpFile): PersistedDraftPhoto? = null
    override fun restore(photo: DraftPhoto): KmpFile? = null
    override fun stablePath(file: KmpFile): String? = null
    override suspend fun delete(photos: List<DraftPhoto>) {}
    override suspend fun deleteAll() {}
}
