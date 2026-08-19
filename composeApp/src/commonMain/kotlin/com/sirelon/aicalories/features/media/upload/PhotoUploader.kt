package com.sirelon.sellsnap.features.media.upload

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface PhotoUploader {
    suspend fun publicUrl(path: String): String
    fun uploadFile(path: String, byteArray: ByteArray): Flow<PhotoUploadStatus>
}

sealed interface PhotoUploadStatus {
    data class Progress(val bytesSent: Long, val totalBytes: Long) : PhotoUploadStatus
    data class Success(val id: String?, val path: String) : PhotoUploadStatus
}

/** Builds a collision-safe storage key from an original file name/path, scoped under [folder]. */
internal fun buildUniqueStoragePath(originalPath: String, folder: String): String {
    val sanitizedName = originalPath
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { originalPath }
        .trim()

    val safeName = sanitizedName
        .replace(Regex("""[^\w.\-]"""), "_")
        .takeIf { it.isNotBlank() }
        ?: "upload_${Uuid.random()}"

    return "$folder/${Uuid.random()}_$safeName"
}
