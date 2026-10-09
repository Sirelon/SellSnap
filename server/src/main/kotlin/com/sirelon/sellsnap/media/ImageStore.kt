package com.sirelon.sellsnap.media

/** Public images for Threads posts, stored in the app bucket under `social/`. */
interface ImageStore {
    /**
     * Stores an owner-sent image as `social/manual/{code}.jpg` or `.png` by [contentType] and
     * returns that object path.
     */
    suspend fun saveManualPhoto(code: String, bytes: ByteArray, contentType: String): String

    /** URL Threads fetches the image from. */
    fun publicUrl(path: String): String
}

/** `social/manual/{code}.png` for PNG, `.jpg` for everything else. */
fun manualPhotoPath(code: String, contentType: String): String =
    "social/manual/$code." + if (contentType == "image/png") "png" else "jpg"
