package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.media.ImageStore

class FakeImageStore : ImageStore {
    val saved = mutableMapOf<String, ByteArray>()

    override suspend fun saveManualPhoto(code: String, jpeg: ByteArray): String {
        val path = "social/manual/$code.jpg"
        saved[path] = jpeg
        return path
    }

    override fun publicUrl(path: String): String = "https://images.test/$path"
}
