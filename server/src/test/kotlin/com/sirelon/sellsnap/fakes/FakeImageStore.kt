package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.media.ImageStore
import com.sirelon.sellsnap.media.manualPhotoPath

class FakeImageStore : ImageStore {
    val saved = mutableMapOf<String, ByteArray>()

    override suspend fun saveManualPhoto(code: String, bytes: ByteArray, contentType: String): String {
        val path = manualPhotoPath(code, contentType)
        saved[path] = bytes
        return path
    }

    override fun publicUrl(path: String): String = "https://images.test/$path"
}
