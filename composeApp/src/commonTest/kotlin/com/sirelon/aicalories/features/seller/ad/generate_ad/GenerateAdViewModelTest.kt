package com.sirelon.sellsnap.features.seller.ad.generate_ad

import androidx.lifecycle.SavedStateHandle
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.RetryStrategy
import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.media.PassthroughImageFormatConverter
import com.sirelon.sellsnap.features.media.upload.DraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import com.sirelon.sellsnap.features.media.upload.MediaUploadHelper
import com.sirelon.sellsnap.features.media.upload.MediaUploadRepository
import com.sirelon.sellsnap.features.media.upload.PersistedDraftPhoto
import com.sirelon.sellsnap.features.media.upload.PhotoUploadStatus
import com.sirelon.sellsnap.features.media.upload.PhotoUploader
import com.sirelon.sellsnap.features.media.upload.UploadedFile
import com.sirelon.sellsnap.features.media.upload.UploadingItem
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.ad.generation_log.NoOpAdGenerationLogRepository
import com.sirelon.sellsnap.features.seller.ad.loadScreenshotPhotos
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.categories.data.CategoriesRepository
import com.sirelon.sellsnap.features.seller.categories.domain.CategoriesMapper
import com.sirelon.sellsnap.features.seller.openai.OpenAIClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertTrue

/**
 * SIR-119: cancelling a running generation must stop it, keep the photos and prompt so a retry
 * can reuse them, and log exactly one `ad_generation_abandoned` - never a second terminal event.
 * The OpenAI call is made to hang via [awaitCancellation] so the test can cancel mid-flight
 * without racing the real 2-minute generation timeout under virtual time (see `runCurrent` below).
 */
class GenerateAdViewModelTest {

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
    fun `cancel stops the generation, keeps the photos, and logs exactly one abandoned event`() =
        runTest(testDispatcher) {
            val harness = buildHarness()
            val uploadsBeforeCancel = harness.viewModel.state.value.uploads

            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
            // runCurrent(), not advanceUntilIdle(): the generation is wrapped in a 2-minute
            // withTimeoutOrNull, and advanceUntilIdle() would fast-forward virtual time straight to
            // that timeout since the hung OpenAI call is the only other pending work.
            runCurrent()

            assertTrue(harness.viewModel.state.value.isLoading, "submit must be loading before cancel can be tested")
            assertEquals(1, harness.analytics.events.count { it.first == AnalyticsEvents.AD_GENERATION_STARTED })

            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Cancel)
            advanceUntilIdle()

            assertFalse(harness.viewModel.state.value.isLoading)
            assertEquals(
                uploadsBeforeCancel,
                harness.viewModel.state.value.uploads,
                "cancel must keep the photos for a retry",
            )
            assertEquals("Nike Air Max 90, worn twice", harness.viewModel.state.value.prompt)

            val abandoned = harness.analytics.events.filter { it.first == AnalyticsEvents.AD_GENERATION_ABANDONED }
            assertEquals(1, abandoned.size, "exactly one abandoned event, never a second terminal event")
            assertEquals("cancel", abandoned.single().second["trigger"])
            assertTrue(harness.analytics.events.none { it.first == AnalyticsEvents.AD_GENERATION_SUCCEEDED })
            assertTrue(harness.analytics.events.none { it.first == AnalyticsEvents.AD_GENERATION_FAILED })
        }

    @Test
    fun `a second Submit before the first starts loading does not start a second generation`() =
        runTest(testDispatcher) {
            val harness = buildHarness()

            // A double-tap dispatches both events before the first submit() coroutine has even
            // started running - isLoading only flips true inside its onStart, well after its first
            // suspension point (currentSession()). The Submit handler's synchronous isLoading guard
            // is what has to catch this, not the flow itself.
            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
            runCurrent()

            assertEquals(
                1,
                harness.analytics.events.count { it.first == AnalyticsEvents.AD_GENERATION_STARTED },
                "a second Submit before isLoading flips true must not start a second generation",
            )

            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Cancel)
            advanceUntilIdle()

            assertEquals(1, harness.analytics.events.count { it.first == AnalyticsEvents.AD_GENERATION_ABANDONED })
        }

    @Test
    fun `cancel during upload leaves the photo ready to upload again, and the retry uploads it`() =
        runTest(testDispatcher) {
            val harness = buildHarness(uploader = HangingPhotoUploader(), initialUpload = UploadingItem())

            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
            runCurrent()
            assertTrue(
                harness.viewModel.state.value.uploads.values.single().isUploading,
                "the pending photo must be uploading before cancel can be tested",
            )

            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Cancel)
            advanceUntilIdle()

            val afterCancel = harness.viewModel.state.value.uploads.values.single()
            assertFalse(afterCancel.isUploading, "a cancelled upload must not keep its spinner")
            assertTrue(afterCancel.isPending, "the photo is simply not uploaded yet")

            // Before the fix, the leftover isUploading flag made the retry skip this photo, so the
            // model got an empty image list and OpenAIClient's require() failed in ~10 ms.
            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
            runCurrent()
            assertTrue(
                harness.viewModel.state.value.uploads.values.single().isUploading,
                "the retry must upload the photo again, not skip it",
            )
            assertEquals(0, harness.viewModel.state.value.completedSteps)
            assertTrue(harness.analytics.events.none { it.first == AnalyticsEvents.AD_GENERATION_FAILED })

            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Cancel)
            advanceUntilIdle()
        }

    // --- test harness -----------------------------------------------------------------------

    private suspend fun buildHarness(
        uploader: PhotoUploader = AlreadyUploadedPhotoUploader(),
        initialUpload: UploadingItem = UploadingItem(
            progress = 100.0,
            uploadedFile = UploadedFile(id = "1", path = "drafts/photo1.jpg"),
        ),
    ): Harness {
        val analytics = FakeAnalytics()
        val engine = MockEngine(
            MockEngineConfig().apply {
                addHandler { awaitCancellation() }
                dispatcher = testDispatcher
            },
        )
        val guestModeStore = GuestModeStore(InMemoryOlxKeyValueStore())
        val countryStore = OlxCountryStore(InMemoryOlxKeyValueStore(), analytics).apply { save(OlxCountry.UA) }
        val errorParser = OlxRemoteErrorParser(testJson)
        val httpClient = createOlxHttpClient(engine)
        val authRepository = OlxAuthRepository(
            httpClient = httpClient,
            credentialsProvider = TestCredentialsProvider(),
            accountStore = OlxAccountStore(InMemoryOlxKeyValueStore(), testJson),
            countryStore = countryStore,
            authSessionStore = OlxAuthSessionStore(InMemoryOlxKeyValueStore(), testJson),
            redirectHandler = TestRedirectHandler(),
            guestModeStore = guestModeStore,
            errorParser = errorParser,
        )

        val categoriesRepository = CategoriesRepository(
            olxApiClient = OlxApiClient(httpClient = httpClient, json = testJson, errorParser = errorParser),
            mapper = CategoriesMapper(),
            scope = CoroutineScope(Dispatchers.Default),
            countryStore = countryStore,
        )
        val openAi = OpenAIClient(
            openAI = OpenAI(
                config = OpenAIConfig(token = "test-token", engine = engine, retry = RetryStrategy(maxRetries = 0)),
            ),
            json = testJson,
            compactJson = testJson,
        )
        val mediaUploadHelper = MediaUploadHelper(
            imageFormatConverter = PassthroughImageFormatConverter(),
            repository = MediaUploadRepository(uploader = uploader),
        )

        val viewModel = GenerateAdViewModel(
            mediaUploadHelper = mediaUploadHelper,
            draftMediaFileStore = FakeDraftMediaFileStore,
            categoriesRepository = categoriesRepository,
            openAi = openAi,
            authRepository = authRepository,
            countryStore = countryStore,
            adFlowTimerStore = AdFlowTimerStore(),
            savedStateHandle = SavedStateHandle(),
            json = testJson,
            analytics = analytics,
            adGenerationLogRepository = NoOpAdGenerationLogRepository,
        )

        authRepository.enterGuestMode()

        // A real bundled photo (already "uploaded"), not a bare KmpFile - see loadScreenshotPhotos
        // KDoc: KmpFile has no common constructor, so this is the only way commonTest code can
        // hand the ViewModel a real one.
        val photoFile = loadScreenshotPhotos().first()
        viewModel.setState {
            it.copy(
                prompt = "Nike Air Max 90, worn twice",
                uploads = mapOf(photoFile to initialUpload),
            )
        }

        return Harness(viewModel, analytics)
    }

    private data class Harness(
        val viewModel: GenerateAdViewModel,
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

    private class AlreadyUploadedPhotoUploader : PhotoUploader {
        override suspend fun publicUrl(path: String): String = "https://example.test/$path"
        override fun uploadFile(path: String, byteArray: ByteArray): Flow<PhotoUploadStatus> =
            flow { error("the test photo is already uploaded - uploadFile must not be called") }
    }

    /** An upload that never finishes, so a test can cancel while it is in flight. */
    private class HangingPhotoUploader : PhotoUploader {
        override suspend fun publicUrl(path: String): String = "https://example.test/$path"
        override fun uploadFile(path: String, byteArray: ByteArray): Flow<PhotoUploadStatus> =
            flow { awaitCancellation() }
    }
}
