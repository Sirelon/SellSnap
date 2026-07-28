package com.sirelon.sellsnap.features.seller.ad

import android.net.Uri
import com.mohamedrejeb.calf.io.KmpFile
import java.io.File

private var androidCacheDir: String = ""

/**
 * Supplies the cache directory used to stage bundled screenshot photos.
 * Call from the Android entry point alongside initAndroidDraftMediaFileStore.
 */
fun initAndroidScreenshotPhotos(cacheDir: String) {
    androidCacheDir = cacheDir
}

internal actual suspend fun writeImageToCache(bytes: ByteArray, fileName: String): KmpFile? {
    if (androidCacheDir.isEmpty()) return null
    val directory = File(androidCacheDir, "screenshot_photos").apply { mkdirs() }
    val destination = File(directory, fileName)
    destination.writeBytes(bytes)
    return KmpFile(Uri.fromFile(destination))
}
