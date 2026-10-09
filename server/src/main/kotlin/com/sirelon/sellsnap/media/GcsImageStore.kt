package com.sirelon.sellsnap.media

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** [ImageStore] on Google Cloud Storage; Threads fetches the image through the Firebase Storage media URL. */
class GcsImageStore(
    private val storage: Storage,
    private val bucket: String,
) : ImageStore {
    override suspend fun saveManualPhoto(code: String, bytes: ByteArray, contentType: String): String {
        val path = manualPhotoPath(code, contentType)
        val info = BlobInfo.newBuilder(BlobId.of(bucket, path)).setContentType(contentType).build()
        withContext(Dispatchers.IO) { storage.create(info, bytes) }
        return path
    }

    override fun publicUrl(path: String): String =
        "https://firebasestorage.googleapis.com/v0/b/$bucket/o/${URLEncoder.encode(path, Charsets.UTF_8)}?alt=media"
}
