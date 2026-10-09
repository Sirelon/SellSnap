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
    override suspend fun saveManualPhoto(code: String, jpeg: ByteArray): String {
        val path = "social/manual/$code.jpg"
        val info = BlobInfo.newBuilder(BlobId.of(bucket, path)).setContentType("image/jpeg").build()
        withContext(Dispatchers.IO) { storage.create(info, jpeg) }
        return path
    }

    override fun publicUrl(path: String): String =
        "https://firebasestorage.googleapis.com/v0/b/$bucket/o/${URLEncoder.encode(path, Charsets.UTF_8)}?alt=media"
}
