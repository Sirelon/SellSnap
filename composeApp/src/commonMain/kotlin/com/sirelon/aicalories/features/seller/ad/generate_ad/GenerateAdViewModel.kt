package com.sirelon.sellsnap.features.seller.ad.generate_ad

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.media.SharedImagesBridge
import com.sirelon.sellsnap.features.media.ui.MAX_PHOTOS
import com.sirelon.sellsnap.features.media.upload.DraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import com.sirelon.sellsnap.features.media.upload.MediaUploadHelper
import com.sirelon.sellsnap.features.media.upload.MediaUploadUpdate
import com.sirelon.sellsnap.features.media.upload.PersistedDraftPhoto
import com.sirelon.sellsnap.features.media.upload.UploadedFile
import com.sirelon.sellsnap.features.media.upload.UploadingItem
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.ad.data.IncompleteGeneratedAdException
import com.sirelon.sellsnap.features.seller.ad.generation_log.AdGenerationAttempt
import com.sirelon.sellsnap.features.seller.ad.generation_log.AdGenerationLogRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.features.seller.ad.loadScreenshotPhotos
import com.sirelon.sellsnap.features.seller.ad.screenshotMode
import com.sirelon.sellsnap.features.seller.categories.data.CategoriesRepository
import com.sirelon.sellsnap.features.seller.categories.data.UnsupportedOlxCategoryException
import com.sirelon.sellsnap.features.seller.openai.AD_GENERATION_MODEL_ID
import com.sirelon.sellsnap.features.seller.openai.AD_GENERATION_PROMPT_VERSION
import com.sirelon.sellsnap.features.seller.openai.AdAnalysis
import com.sirelon.sellsnap.features.seller.openai.OpenAIClient
import com.sirelon.sellsnap.features.seller.openai.UnusablePhotoReason
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_category_not_supported
import com.sirelon.sellsnap.generated.resources.error_generate_ad_failed
import com.sirelon.sellsnap.generated.resources.error_photo_different_items
import com.sirelon.sellsnap.generated.resources.error_photo_unreadable
import com.sirelon.sellsnap.generated.resources.error_selected_files_process_failed
import com.sirelon.sellsnap.generated.resources.error_upload_file_failed
import com.sirelon.sellsnap.generated.resources.photos_limit_kept_message
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

private const val GuestProcessingStepCount = 3
private const val AuthenticatedProcessingStepCount = 5
private const val GenerateAdSavedStateKey = "generate_ad_saved_state"
private const val OpenAIRequestFailedPrefix = "OpenAI request failed:"

/**
 * How long the model gets to answer before the generation is called off.
 *
 * Measured p50 is ~16s and the worst observed run was 96s, so this is roughly a 25% margin over
 * anything real. The alternative is the OpenAI client's own 5-minute request timeout, which in
 * practice means a spinner the seller force-quits out of - and a force-quit leaves no event
 * behind, so those runs were invisible in analytics too.
 */
private val AdGenerationTimeout = 2.minutes

/** Thrown instead of letting `withTimeout` raise a [kotlinx.coroutines.CancellationException],
 * which [kotlinx.coroutines.flow.catch] would pass straight through as a cancellation rather than
 * a failure the seller can be told about and retry. */
private class AdGenerationTimeoutException : Exception("Ad generation exceeded $AdGenerationTimeout")

class GenerateAdViewModel(
    private val mediaUploadHelper: MediaUploadHelper,
    private val draftMediaFileStore: DraftMediaFileStore,
    private val categoriesRepository: CategoriesRepository,
    private val openAi: OpenAIClient,
    private val authRepository: OlxAuthRepository,
    private val countryStore: OlxCountryStore,
    private val adFlowTimerStore: AdFlowTimerStore,
    private val savedStateHandle: SavedStateHandle,
    private val json: Json,
    private val analytics: Analytics,
    private val adGenerationLogRepository: AdGenerationLogRepository,
) : BaseViewModel<GenerateAdContract.GenerateAdState, GenerateAdContract.GenerateAdEvent, GenerateAdContract.GenerateAdEffect>() {

    private val restoredSavedState = readSavedState()

    init {
        state
            .drop(1)
            .map(::toSavedState)
            .distinctUntilChanged()
            .onEach { snapshot -> savedStateHandle[GenerateAdSavedStateKey] = json.encodeToString(snapshot) }
            .launchIn(viewModelScope)

        if (screenshotMode) seedScreenshotPhotos()
        observeSharedImages()
    }

    /**
     * Photos shared in from the OS share sheet (MainActivity.publishSharedImages) arrive on this
     * same bridge AppNavigationViewModel used to route here - consumed the same way manually
     * picked photos are.
     */
    private fun observeSharedImages() {
        SharedImagesBridge.pending
            .filterNotNull()
            .onEach { files ->
                SharedImagesBridge.consume()
                onFileResult(GenerateAdContract.GenerateAdEvent.UploadFilesResult(result = Result.success(files)))
            }
            .launchIn(viewModelScope)
    }

    /**
     * Screenshot runs cannot reliably drive the OS photo picker (see ScreenshotPhotos.kt),
     * so the bundled test photos are injected through the very same event the picker emits.
     * Skipped when photos were already restored, so a process death does not duplicate them.
     */
    private fun seedScreenshotPhotos() {
        viewModelScope.launch {
            if (currentState().uploads.isNotEmpty()) return@launch
            val files = loadScreenshotPhotos()
            if (files.isEmpty()) return@launch
            onFileResult(
                GenerateAdContract.GenerateAdEvent.UploadFilesResult(result = Result.success(files)),
            )
        }
    }

    override fun onEvent(event: GenerateAdContract.GenerateAdEvent) {
        when (event) {
            is GenerateAdContract.GenerateAdEvent.PromptChanged -> {
                setState {
                    it.copy(
                        prompt = event.value,
                        errorMessage = null,
                    )
                }
            }

            is GenerateAdContract.GenerateAdEvent.Submit -> {
                viewModelScope.launch {
                    submit()
                }
            }

            is GenerateAdContract.GenerateAdEvent.UploadFilesResult -> onFileResult(event)

            is GenerateAdContract.GenerateAdEvent.RemovePhoto -> {
                val removedPhoto = photoForFile(event.file)
                setState { current ->
                    val updatedUploads = current.uploads - event.file
                    if (updatedUploads.isEmpty()) {
                        adFlowTimerStore.clear()
                    }
                    current.copy(uploads = updatedUploads)
                }
                removedPhoto?.let { photo ->
                    viewModelScope.launch { draftMediaFileStore.delete(listOf(photo)) }
                }
            }

        }
    }

    private suspend fun submit() {
        val isGuest = authRepository.currentSession().mode == SellerSessionMode.Guest
        val generationSessionId = Uuid.random().toString()
        var generationAttemptId: String? = null
        // Per-attempt, unlike AdFlowTimerStore, whose mark spans the whole ad flow and survives a
        // retry - so it cannot answer "how long did this generation take?", which is the number
        // the 96s outlier was only visible in by diffing event timestamps in BigQuery.
        val startedAt = TimeSource.Monotonic.markNow()
        // Every ad_generation_started needs exactly one terminal event against it. Without this
        // latch, leaving the screen mid-generation ends the flow through onCompletion having
        // logged nothing, and the attempt is indistinguishable from one that silently vanished.
        var outcomeLogged = false

        flowOf(1)
            .onStart {
                adFlowTimerStore.markFlowStartedIfNeeded()
                analytics.logEvent(
                    AnalyticsEvents.AD_GENERATION_STARTED,
                    mapOf("is_guest" to isGuest),
                )
                setState {
                    it.copy(
                        isLoading = true,
                        isGuestMode = isGuest,
                        completedSteps = 0,
                        errorMessage = null,
                    )
                }
            }

            .map { uploadFilesAndGetPublicUrls() }
            .onEach { setState { it.copy(completedSteps = 1) } }

            .map { images ->
                val analysis = withTimeoutOrNull(AdGenerationTimeout) {
                    openAi.analyzeThing(
                        images = images,
                        sellerPrompt = state.value.prompt,
                        country = countryStore.current,
                    )
                } ?: throw AdGenerationTimeoutException()
                images to analysis
            }

            .flatMapLatest { (images, analysis) ->
                if (analysis is AdAnalysis.Unusable) {
                    // The seller has something to fix, so the flow stops here rather than opening a
                    // preview of a listing the model never managed to write.
                    logUnusablePhotos(analysis.reason, generationSessionId, images, startedAt.elapsedNow().inWholeMilliseconds)
                    outcomeLogged = true
                    return@flatMapLatest emptyFlow()
                }

                val generatedAd = (analysis as AdAnalysis.Generated).advertisement
                setState { it.copy(completedSteps = 2) }
                generationAttemptId = adGenerationLogRepository.logAttempt(
                    AdGenerationAttempt(
                        sessionId = generationSessionId,
                        attemptNumber = 0,
                        previousAttemptId = null,
                        countryCode = countryStore.current.code,
                        modelId = AD_GENERATION_MODEL_ID,
                        promptVersion = AD_GENERATION_PROMPT_VERSION,
                        imagePaths = generatedAd.images,
                        sellerPrompt = state.value.prompt,
                        title = generatedAd.title,
                        description = generatedAd.description,
                        suggestedPrice = generatedAd.suggestedPrice,
                        minPrice = generatedAd.minPrice,
                        maxPrice = generatedAd.maxPrice,
                    )
                )

                if (isGuest) {
                    flowOf(
                        AdvertisementWithAttributes(
                            advertisement = generatedAd,
                            filledAttributes = emptyMap(),
                            sellerPrompt = state.value.prompt,
                            generationSessionId = generationSessionId,
                            lastAttemptId = generationAttemptId,
                        )
                    ).onEach {
                        setState { it.copy(completedSteps = GuestProcessingStepCount) }
                    }
                } else {
                    categoriesRepository
                        .categorySuggestion(generatedAd.title)
                        .onEach { setState { it.copy(completedSteps = 3) } }

                        .flatMapLatest { categoriesRepository.getAttributes(it.id) }
                        .onEach { setState { it.copy(completedSteps = 4) } }
                        .map {
                            openAi.fillAdditionalInfo(
                                previousResponseId = analysis.responseId,
                                attributes = it,
                                sellerPrompt = state.value.prompt
                            )
                        }
                        .onEach { setState { it.copy(completedSteps = AuthenticatedProcessingStepCount) } }
                        .map {
                            AdvertisementWithAttributes(
                                advertisement = generatedAd,
                                filledAttributes = it,
                                sellerPrompt = state.value.prompt,
                                generationSessionId = generationSessionId,
                                lastAttemptId = generationAttemptId,
                            )
                        }
                }
            }

            .onEach { ad ->
                adFlowTimerStore.markGenerationCompleted()
                outcomeLogged = true
                analytics.logEvent(
                    AnalyticsEvents.AD_GENERATION_SUCCEEDED,
                    mapOf("duration_ms" to startedAt.elapsedNow().inWholeMilliseconds),
                )
                clearDraft()
                postEffect(GenerateAdContract.GenerateAdEffect.OpenAdPreview(ad = ad))
            }
            .catch { error ->
                val message = if (error is UnsupportedOlxCategoryException) {
                    getString(Res.string.error_category_not_supported)
                } else {
                    error.toGenerateAdErrorMessage(
                        defaultMessage = getString(Res.string.error_generate_ad_failed),
                    )
                }
                outcomeLogged = true
                analytics.recordException(error, AnalyticsEvents.AD_GENERATION_FAILED)
                analytics.logEvent(
                    AnalyticsEvents.AD_GENERATION_FAILED,
                    mapOf(
                        "reason" to error.toFailureReason(),
                        "duration_ms" to startedAt.elapsedNow().inWholeMilliseconds,
                    ),
                )
                setState { it.copy(isLoading = false) }
                showError(message)
            }
            .onCompletion {
                if (!outcomeLogged) {
                    // The seller left the screen, or the process is going down, while the
                    // generation was still running. `completed_steps` says how far it got: 0 photos
                    // still uploading, 1 uploading done, 2 model answered.
                    analytics.logEvent(
                        AnalyticsEvents.AD_GENERATION_ABANDONED,
                        mapOf(
                            "duration_ms" to startedAt.elapsedNow().inWholeMilliseconds,
                            "completed_steps" to currentState().completedSteps,
                        ),
                    )
                }
                setState { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun uploadFilesAndGetPublicUrls(): List<String> {
        val uploads = currentState().uploads
        val pendingFiles = uploads
            .filter { (_, item) -> item.isPending }
            .keys.toList()
        val uploadedByFile = uploads
            .mapNotNull { (file, item) -> item.uploadedFile?.let { file to it } }
            .toMap()

        val newlyUploadedByFile = if (pendingFiles.isEmpty()) {
            emptyMap()
        } else {
            mediaUploadHelper
                .uploadPreparedFiles(pendingFiles)
                .onEach(::handleUploadUpdate)
                .mapNotNull { update ->
                    when (update) {
                        is MediaUploadUpdate.Success -> update.file to update.uploadedFile
                        is MediaUploadUpdate.Failure -> throw IllegalStateException(update.message)
                        is MediaUploadUpdate.Error -> throw IllegalStateException(update.message)
                        else -> null
                    }
                }
                .toList()
                .toMap()
        }

        return uploads.keys.mapNotNull { file ->
            (newlyUploadedByFile[file] ?: uploadedByFile[file])?.toPublicUrl()
        }
    }

    private suspend fun UploadedFile.toPublicUrl(): String = mediaUploadHelper.publicUrl(path)

    /**
     * Caps the running total at [MAX_PHOTOS], not just this one batch.
     *
     * The gallery picker's own `maxItems` (see `GenerateAdScreen`) only bounds a single pick - it
     * has no way to know how many photos are already on the grid, so a seller who already has
     * photos and then picks a fresh batch could otherwise land above the cap. Files dropped here
     * were already written to disk by [draftMediaFileStore], so they are deleted rather than left
     * orphaned.
     */
    private fun onFileResult(event: GenerateAdContract.GenerateAdEvent.UploadFilesResult) {
        viewModelScope.launch {
            mediaUploadHelper
                // Screenshot-mode photos are bundled resources with a known-supported format
                // (see ScreenshotPhotos.kt), so the format check — and the Calf Android-context
                // requirement it carries — is skipped for them. Unaffected when screenshotMode
                // is false: validateFormat then evaluates to true, i.e. the prior behavior.
                .prepareFiles(selectionResult = event.result, validateFormat = !screenshotMode)
                .mapCatching { files -> files.mapNotNull { draftMediaFileStore.persist(it) } }
                .onSuccess { persistedFiles ->
                    val existingCount = currentState().uploads.size
                    if (persistedFiles.isNotEmpty() && existingCount == 0) {
                        adFlowTimerStore.markFlowStartedIfNeeded()
                    }
                    val newFiles = persistedFiles.filter { !currentState().uploads.containsKey(it.file) }
                    val (kept, dropped) = splitAtPhotoLimit(newFiles, existingCount, MAX_PHOTOS)
                    setState { current ->
                        current.copy(uploads = current.uploads + kept.associate { it.file to UploadingItem() })
                    }
                    if (dropped.isNotEmpty()) {
                        draftMediaFileStore.delete(dropped.map { it.photo })
                        postEffect(
                            GenerateAdContract.GenerateAdEffect.ShowMessage(
                                getString(Res.string.photos_limit_kept_message, MAX_PHOTOS)
                            )
                        )
                    }
                }
                .onFailure { error ->
                    showError(getString(Res.string.error_selected_files_process_failed))
                }
        }
    }

    private suspend fun handleUploadUpdate(update: MediaUploadUpdate) {
        when (update) {
            MediaUploadUpdate.Started -> {
                setState { it.copy(errorMessage = null) }
            }

            is MediaUploadUpdate.AddPlaceholder -> {
                addUploadPlaceholder(update.file)
            }

            is MediaUploadUpdate.UploadStarted -> {
                updateUpload(file = update.file) { item ->
                    item.copy(isUploading = true)
                }
            }

            is MediaUploadUpdate.Progress -> {
                updateUpload(file = update.file) { item ->
                    item.copy(progress = update.progress)
                }
            }

            is MediaUploadUpdate.Success -> {
                updateUpload(file = update.file) { item ->
                    item.copy(
                        isUploading = false,
                        progress = 100.0,
                        uploadedFile = update.uploadedFile,
                    )
                }
            }

            is MediaUploadUpdate.Failure -> {
                analytics.recordException(update.cause, AnalyticsEvents.PHOTO_UPLOAD_FAILED)
                handleUploadFailure(file = update.file, message = getString(Res.string.error_upload_file_failed))
            }

            is MediaUploadUpdate.Error -> {
                showError(getString(Res.string.error_upload_file_failed))
            }
        }
    }

    private fun addUploadPlaceholder(file: KmpFile) {
        setState { current ->
            if (current.uploads.containsKey(file)) {
                current
            } else {
                current.copy(
                    uploads = current.uploads + (file to UploadingItem())
                )
            }
        }
    }

    private fun handleUploadFailure(file: KmpFile, message: String) {
        val failedPhoto = photoForFile(file)
        setState { current ->
            val updatedUploads = current.uploads - file
            if (updatedUploads.isEmpty()) {
                adFlowTimerStore.clear()
            }
            current.copy(
                errorMessage = message,
                uploads = updatedUploads,
            )
        }
        failedPhoto?.let { photo ->
            viewModelScope.launch { draftMediaFileStore.delete(listOf(photo)) }
        }
        postEffect(GenerateAdContract.GenerateAdEffect.ShowMessage(message))
    }

    /**
     * The model read the photos and declined to write a listing. That is an outcome the seller can
     * act on, not a breakage, so it carries its own event and never counts as a generation failure.
     * The attempt is still recorded - reason set, copy empty - so refusals stay measurable next to
     * the attempts that did produce a listing.
     */
    private suspend fun logUnusablePhotos(
        reason: UnusablePhotoReason,
        generationSessionId: String,
        images: List<String>,
        durationMs: Long,
    ) {
        adGenerationLogRepository.logAttempt(
            AdGenerationAttempt(
                sessionId = generationSessionId,
                attemptNumber = 0,
                previousAttemptId = null,
                countryCode = countryStore.current.code,
                modelId = AD_GENERATION_MODEL_ID,
                promptVersion = AD_GENERATION_PROMPT_VERSION,
                imagePaths = images,
                sellerPrompt = state.value.prompt,
                title = "",
                description = "",
                suggestedPrice = 0f,
                minPrice = 0f,
                maxPrice = 0f,
                unrecognized = reason.code,
            )
        )
        analytics.logEvent(
            AnalyticsEvents.AD_GENERATION_PHOTOS_UNUSABLE,
            mapOf("reason" to reason.code, "duration_ms" to durationMs),
        )
        showError(getString(reason.messageRes))
    }

    private fun showError(message: String) {
        setState { it.copy(errorMessage = message) }
        postEffect(GenerateAdContract.GenerateAdEffect.ShowMessage(message))
    }

    private fun Throwable.toGenerateAdErrorMessage(defaultMessage: String): String =
        message
            ?.trim()
            ?.takeIf { it.startsWith(OpenAIRequestFailedPrefix) }
            ?.removePrefix(OpenAIRequestFailedPrefix)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultMessage

    private fun Throwable.toFailureReason(): String = when {
        this is UnsupportedOlxCategoryException -> "unsupported_category"
        this is IncompleteGeneratedAdException -> "incomplete_ad"
        this is AdGenerationTimeoutException -> "timeout"
        message?.startsWith(OpenAIRequestFailedPrefix) == true -> "openai_error"
        else -> "other"
    }

    private fun updateUpload(
        file: KmpFile,
        reducer: (UploadingItem) -> UploadingItem,
    ) {
        setState { current ->
            val existing = current.uploads[file] ?: return@setState current
            current.copy(
                uploads = current.uploads + (file to reducer(existing))
            )
        }
    }

    override fun initialState(): GenerateAdContract.GenerateAdState {
        val uploads = restoredSavedState.photos.mapNotNull { photo ->
            val file = draftMediaFileStore.restore(photo) ?: return@mapNotNull null
            file to UploadingItem(
                progress = if (photo.uploadedPath != null) 100.0 else 0.0,
                uploadedFile = photo.uploadedPath?.let { UploadedFile(id = photo.uploadedId, path = it) },
            )
        }.toMap()
        return GenerateAdContract.GenerateAdState(
            prompt = restoredSavedState.prompt,
            uploads = uploads,
        )
    }

    private fun readSavedState(): GenerateAdSavedState =
        savedStateHandle.get<String>(GenerateAdSavedStateKey)
            ?.let { runCatching { json.decodeFromString<GenerateAdSavedState>(it) }.getOrNull() }
            ?: GenerateAdSavedState()

    private fun toSavedState(state: GenerateAdContract.GenerateAdState): GenerateAdSavedState {
        val existingByPath = readSavedState().photos.associateBy { it.path }
        val photos = state.uploads.mapNotNull { (file, item) ->
            val path = draftMediaFileStore.stablePath(file) ?: return@mapNotNull null
            val existing = existingByPath[path]
            DraftPhoto(
                id = existing?.id ?: path,
                path = path,
                displayName = existing?.displayName ?: file.getName(),
                uploadedId = item.uploadedFile?.id ?: existing?.uploadedId,
                uploadedPath = item.uploadedFile?.path ?: existing?.uploadedPath,
            )
        }
        return GenerateAdSavedState(prompt = state.prompt, photos = photos)
    }

    private fun clearDraft() {
        val photos = readSavedState().photos
        viewModelScope.launch { draftMediaFileStore.delete(photos) }
        setState { GenerateAdContract.GenerateAdState() }
    }

    private fun photoForFile(file: KmpFile): DraftPhoto? {
        val path = draftMediaFileStore.stablePath(file) ?: return null
        return readSavedState().photos.firstOrNull { it.path == path }
    }
}

/**
 * Splits a freshly persisted batch into what still fits under [max] given [existingCount] photos
 * already on the grid, and what has to be dropped. Pure so the running-total cap (see
 * [GenerateAdViewModel.onFileResult]) is testable without the ViewModel's upload machinery.
 */
internal fun splitAtPhotoLimit(
    newFiles: List<PersistedDraftPhoto>,
    existingCount: Int,
    max: Int,
): Pair<List<PersistedDraftPhoto>, List<PersistedDraftPhoto>> {
    val remainingSlots = (max - existingCount).coerceAtLeast(0)
    return newFiles.take(remainingSlots) to newFiles.drop(remainingSlots)
}

/**
 * Blur, darkness and an empty frame are one state to the seller - the photo did not work, take
 * another - so they share a message. The codes stay separate in the generation log, where telling
 * them apart is what would justify ever splitting the copy again.
 */
private val UnusablePhotoReason.messageRes: StringResource
    get() = when (this) {
        UnusablePhotoReason.TooBlurry,
        UnusablePhotoReason.TooDark,
        UnusablePhotoReason.NotAnItem,
        -> Res.string.error_photo_unreadable

        UnusablePhotoReason.DifferentItems -> Res.string.error_photo_different_items
    }
