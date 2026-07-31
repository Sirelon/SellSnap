package com.sirelon.sellsnap.features.seller.ad

import com.mohamedrejeb.calf.io.KmpFile
import java.io.File

internal actual suspend fun writeImageToCache(bytes: ByteArray, fileName: String): KmpFile? {
    val directory = File(System.getProperty("java.io.tmpdir"), "sellsnap_screenshot_photos")
        .apply { mkdirs() }
    val destination = File(directory, fileName)
    destination.writeBytes(bytes)
    return KmpFile(destination)
}
