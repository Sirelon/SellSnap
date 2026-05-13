package com.sirelon.sellsnap.features.seller.ad.generate_ad

import androidx.lifecycle.viewModelScope
import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.media.upload.MediaUploadHelper
import com.sirelon.sellsnap.features.media.upload.MediaUploadUpdate
import com.sirelon.sellsnap.features.media.upload.UploadedFile
import com.sirelon.sellsnap.features.media.upload.UploadingItem
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.features.seller.categories.data.CategoriesRepository
import com.sirelon.sellsnap.features.seller.openai.OpenAIClient
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_generate_ad_failed
import com.sirelon.sellsnap.generated.resources.error_selected_files_process_failed
import com.sirelon.sellsnap.generated.resources.error_upload_file_failed
import kotlinx.coroutines.flow.catch
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

private const val GuestProcessingStepCount = 3
private const val AuthenticatedProcessingStepCount = 5

class GenerateAdViewModel(
    private val mediaUploadHelper: MediaUploadHelper,
    private val categoriesRepository: CategoriesRepository,
    private val openAi: OpenAIClient,
    private val accountRepository: SellerAccountRepository,
    private val authRepository: OlxAuthRepository,
    private val adFlowTimerStore: AdFlowTimerStore,
) : BaseViewModel<GenerateAdContract.GenerateAdState, GenerateAdContract.GenerateAdEvent, GenerateAdContract.GenerateAdEffect>() {

    init {
        accountRepository.user
            .onEach { user ->
                setState {
                    it.copy(
                        profileName = user?.name?.takeIf { name -> name.isNotBlank() },
                        profileAvatarUrl = user?.avatar,
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            accountRepository.refreshProfile()
        }
    }

    override fun initialState(): GenerateAdContract.GenerateAdState =
        GenerateAdContract.GenerateAdState()

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
                setState { current ->
                    val updatedUploads = current.uploads - event.file
                    if (updatedUploads.isEmpty()) {
                        adFlowTimerStore.clear()
                    }
                    current.copy(uploads = updatedUploads)
                }
            }
        }
    }

    private suspend fun submit() {
        val isGuest = authRepository.currentSession().mode == SellerSessionMode.Guest

        flowOf(1)
            .onStart {
                adFlowTimerStore.markFlowStartedIfNeeded()
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

            .map { openAi.analyzeThing(images = it, sellerPrompt = state.value.prompt) }
            .onEach { setState { it.copy(completedSteps = 2) } }

            .flatMapLatest { data ->
                if (isGuest) {
                    flowOf(
                        AdvertisementWithAttributes(
                            advertisement = data.second,
                            filledAttributes = emptyMap(),
                        )
                    ).onEach {
                        setState { it.copy(completedSteps = GuestProcessingStepCount) }
                    }
                } else {
                    categoriesRepository
                        .categorySuggestion(data.second.title)
                        .onEach { setState { it.copy(completedSteps = 3) } }

                        .flatMapLatest { categoriesRepository.getAttributes(it.id) }
                        .onEach { setState { it.copy(completedSteps = 4) } }
                        .map {
                            openAi.fillAdditionalInfo(
                                previousResponseId = data.first,
                                attributes = it,
                                sellerPrompt = state.value.prompt
                            )
                        }
                        .onEach {
                            setState { it.copy(completedSteps = AuthenticatedProcessingStepCount) }
                        }
                        .map {
                            AdvertisementWithAttributes(
                                advertisement = data.second,
                                filledAttributes = it
                            )
                        }
                }
            }

            .onEach {
                adFlowTimerStore.markGenerationCompleted()
                postEffect(GenerateAdContract.GenerateAdEffect.OpenAdPreview(ad = it))
            }
            .catch { error ->
                showError(getString(Res.string.error_generate_ad_failed))
            }
            .onCompletion {
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
                .filterIsInstance<MediaUploadUpdate.Success>()
                .map { it.file to it.uploadedFile }
                .toList()
                .toMap()
        }

        return uploads.keys.mapNotNull { file ->
            (newlyUploadedByFile[file] ?: uploadedByFile[file])?.toPublicUrl()
        }
    }

    private fun UploadedFile.toPublicUrl(): String = mediaUploadHelper.publicUrl(path)

    private fun onFileResult(event: GenerateAdContract.GenerateAdEvent.UploadFilesResult) {
        viewModelScope.launch {
            mediaUploadHelper
                .prepareFiles(selectionResult = event.result)
                .onSuccess { files ->
                    if (files.isNotEmpty() && currentState().uploads.isEmpty()) {
                        adFlowTimerStore.markFlowStartedIfNeeded()
                    }
                    setState { current ->
                        val newEntries = files
                            .filter { file -> !current.uploads.containsKey(file) }
                            .associateWith { UploadingItem() }
                        current.copy(uploads = current.uploads + newEntries)
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
        postEffect(GenerateAdContract.GenerateAdEffect.ShowMessage(message))
    }

    private fun showError(message: String) {
        setState { it.copy(errorMessage = message) }
        postEffect(GenerateAdContract.GenerateAdEffect.ShowMessage(message))
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
}
