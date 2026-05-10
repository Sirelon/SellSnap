package com.sirelon.sellsnap.features.seller.ad.generate_ad

import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.features.media.upload.UploadingItem
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes

interface GenerateAdContract {

    data class GenerateAdState(
        val prompt: String = "",
        val isLoading: Boolean = false,
        val isGuestMode: Boolean = false,
        val completedSteps: Int = 0,
        val errorMessage: String? = null,
        val uploads: Map<KmpFile, UploadingItem> = emptyMap(),
        val profileName: String? = null,
        val profileAvatarUrl: String? = null,
    ) {
        val canSubmit: Boolean
            get() = !isLoading && uploads.isNotEmpty()
    }

    sealed interface GenerateAdEvent {
        data class PromptChanged(val value: String) : GenerateAdEvent

        data class UploadFilesResult(val result: Result<List<KmpFile>>) : GenerateAdEvent

        data class RemovePhoto(val file: KmpFile) : GenerateAdEvent

        data object Submit : GenerateAdEvent
    }

    sealed interface GenerateAdEffect {
        data class ShowMessage(val message: String) : GenerateAdEffect

        data class OpenAdPreview(
            val ad: AdvertisementWithAttributes,
        ) : GenerateAdEffect
    }
}
