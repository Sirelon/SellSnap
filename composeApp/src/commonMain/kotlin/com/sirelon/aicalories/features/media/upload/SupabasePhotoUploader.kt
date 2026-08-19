package com.sirelon.sellsnap.features.media.upload

import com.sirelon.sellsnap.supabase.SupabaseClient
import io.github.jan.supabase.storage.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabasePhotoUploader(
    private val client: SupabaseClient,
) : PhotoUploader {

    override suspend fun publicUrl(path: String): String = client.publicUrl(path)

    override fun uploadFile(path: String, byteArray: ByteArray): Flow<PhotoUploadStatus> =
        client.uploadFile(path, byteArray).map { status ->
            when (status) {
                is UploadStatus.Progress -> PhotoUploadStatus.Progress(
                    bytesSent = status.totalBytesSend,
                    totalBytes = status.contentLength,
                )

                is UploadStatus.Success -> PhotoUploadStatus.Success(
                    id = status.response.id,
                    path = status.response.path,
                )
            }
        }
}
