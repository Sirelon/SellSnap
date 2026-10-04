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
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import com.sirelon.sellsnap.features.common.presentation.awaitEffect
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
import kotlinx.serialization.builtins.serializer
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
 *
 * SIR-121: [buildGenerationStageParams] is a pure top-level function, so its own tests below don't
 * need the ViewModel harness at all.
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
    fun `a succeeded run carries every finished stage`() {
        val params = buildGenerationStageParams(
            durationMs = 5_000L,
            photoCount = 3,
            uploadMs = 1_000L,
            uploadBytes = 2_400_000L,
            modelMs = 3_200L,
            attributesMs = 700L,
        )

        assertEquals(5_000L, params["duration_ms"])
        assertEquals(3, params["photo_count"])
        assertEquals(1_000L, params["upload_ms"])
        assertEquals(2_400_000L, params["upload_bytes"])
        assertEquals(3_200L, params["model_ms"])
        assertEquals(700L, params["attributes_ms"])
    }

    @Test
    fun `a guest run never carries attributes_ms`() {
        val params = buildGenerationStageParams(
            durationMs = 4_000L,
            photoCount = 2,
            uploadMs = 800L,
            uploadBytes = 1_600_000L,
            modelMs = 2_500L,
            attributesMs = null,
        )

        assertFalse(params.containsKey("attributes_ms"), "guest flow never fills attributes, so the param must be absent, not zero")
        assertTrue(params.containsKey("upload_ms"))
        assertTrue(params.containsKey("model_ms"))
    }

    @Test
    fun `a run that failed during upload carries only duration_ms and photo_count`() {
        val params = buildGenerationStageParams(
            durationMs = 900L,
            photoCount = 3,
            uploadMs = null,
            uploadBytes = null,
            modelMs = null,
            attributesMs = null,
        )

        assertEquals(setOf("duration_ms", "photo_count"), params.keys)
    }

    @Test
    fun `a run abandoned mid-model-call carries the finished upload stage only`() {
        val params = buildGenerationStageParams(
            durationMs = 6_000L,
            photoCount = 1,
            uploadMs = 1_200L,
            uploadBytes = 900_000L,
            modelMs = null,
            attributesMs = null,
        )

        assertEquals(setOf("duration_ms", "photo_count", "upload_ms", "upload_bytes"), params.keys)
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

    @Test
    fun `an empty answer is retried once with the same photo urls and the second answer succeeds`() =
        runTest(testDispatcher) {
            val requestBodies = mutableListOf<String>()
            val harness = buildHarness(handler = { request ->
                requestBodies += (request.body as TextContent).text
                respondWithListing(if (requestBodies.size == 1) EMPTY_LISTING else VALID_LISTING)
            })

            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
            val effect = harness.viewModel.effects.awaitEffect<GenerateAdContract.GenerateAdEffect.OpenAdPreview>()

            assertEquals(2, requestBodies.size)
            assertEquals(requestBodies[0], requestBodies[1], "the retry sends the same photo urls, nothing re-uploaded")
            assertEquals("Nike Air Max 90", effect.ad.advertisement.title)
            val succeeded = harness.analytics.events.single { it.first == AnalyticsEvents.AD_GENERATION_SUCCEEDED }
            assertEquals(1, succeeded.second["retry_count"])
            assertTrue(harness.analytics.events.none { it.first == AnalyticsEvents.AD_GENERATION_FAILED })
        }

    @Test
    fun `an answer that is fine first time logs retry_count 0`() = runTest(testDispatcher) {
        val harness = buildHarness(handler = { respondWithListing(VALID_LISTING) })

        harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
        harness.viewModel.effects.awaitEffect<GenerateAdContract.GenerateAdEffect.OpenAdPreview>()

        val succeeded = harness.analytics.events.single { it.first == AnalyticsEvents.AD_GENERATION_SUCCEEDED }
        assertEquals(0, succeeded.second["retry_count"])
    }

    @Test
    fun `two empty answers fail with reason empty_output after exactly one retry`() =
        runTest(testDispatcher) {
            var requests = 0
            val harness = buildHarness(handler = {
                requests++
                respondWithListing(EMPTY_LISTING)
            })

            harness.viewModel.onEvent(GenerateAdContract.GenerateAdEvent.Submit)
            harness.viewModel.effects.awaitEffect<GenerateAdContract.GenerateAdEffect.ShowMessage>()

            assertEquals(2, requests, "one try plus exactly one retry")
            val failed = harness.analytics.events.single { it.first == AnalyticsEvents.AD_GENERATION_FAILED }
            assertEquals("empty_output", failed.second["reason"])
            assertEquals(1, failed.second["retry_count"])
            assertTrue(harness.analytics.events.none { it.first == AnalyticsEvents.AD_GENERATION_SUCCEEDED })
            assertFalse(harness.viewModel.state.value.isLoading)
        }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondWithListing(listing: String) = respond(
        content = """{"id":"resp_1","status":"completed","output":[{"id":"msg_1","type":"message","role":"assistant","content":[{"type":"output_text","text":${Json.encodeToString(String.serializer(), listing)}}]}]}""",
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    // --- test harness -----------------------------------------------------------------------

    private suspend fun buildHarness(
        uploader: PhotoUploader = AlreadyUploadedPhotoUploader(),
        initialUpload: UploadingItem = UploadingItem(
            progress = 100.0,
            uploadedFile = UploadedFile(id = "1", path = "drafts/photo1.jpg"),
        ),
        handler: MockRequestHandler = { awaitCancellation() },
    ): Harness {
        val analytics = FakeAnalytics()
        val engine = MockEngine(
            MockEngineConfig().apply {
                addHandler(handler)
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

    private companion object {
        const val EMPTY_LISTING = """{"title":"","description":"","suggestedPrice":0,"minPrice":0,"maxPrice":0}"""
        const val VALID_LISTING =
            """{"title":"Nike Air Max 90","description":"Worn twice, clean sole.","suggestedPrice":1500,"minPrice":1200,"maxPrice":1800}"""
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
