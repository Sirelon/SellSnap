package com.sirelon.sellsnap.features.media.upload

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.installations.installations
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.storage.storageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.dsl.module

internal class FirebasePhotoUploader : PhotoUploader {

    override suspend fun publicUrl(path: String): String =
        Firebase.storage.reference(path).getDownloadUrl()

    override fun uploadFile(path: String, byteArray: ByteArray): Flow<PhotoUploadStatus> = flow {
        val storagePath = buildUniqueStoragePath(path, folder = Firebase.installations.getId())
        val reference = Firebase.storage.reference(storagePath)
        val metadata = storageMetadata { contentType = contentTypeForPath(path) }

        emitAll(
            reference.putDataResumable(firebaseUploadData(byteArray), metadata).map { progress ->
                PhotoUploadStatus.Progress(
                    bytesSent = progress.bytesTransferred.toLong(),
                    totalBytes = progress.totalByteCount.toLong(),
                )
            },
        )
        emit(PhotoUploadStatus.Success(id = null, path = storagePath))
    }
}

private fun contentTypeForPath(path: String): String =
    when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

actual val photoUploaderModule: Module = module {
    single<PhotoUploader> { FirebasePhotoUploader() }
}

internal expect fun firebaseUploadData(byteArray: ByteArray): Data
