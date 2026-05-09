package com.sirelon.sellsnap.features.media.upload

data class UploadingItem(
    val progress: Double = 0.0,
    val isUploading: Boolean = false,
    val uploadedFile: UploadedFile? = null,
    val error: String? = null,
) {
    val isPending: Boolean get() = !isUploading && uploadedFile == null && error == null
    val isUploaded: Boolean get() = uploadedFile != null
    val hasFailed: Boolean get() = error != null
}
