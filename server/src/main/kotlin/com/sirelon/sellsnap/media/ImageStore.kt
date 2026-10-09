package com.sirelon.sellsnap.media

/** Public images for Threads posts, stored in the app bucket under `social/`. */
interface ImageStore {
    /** Stores an owner-sent photo as `social/manual/{code}.jpg` and returns that object path. */
    suspend fun saveManualPhoto(code: String, jpeg: ByteArray): String

    /** URL Threads fetches the image from. */
    fun publicUrl(path: String): String
}
