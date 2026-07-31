package com.sirelon.sellsnap.features.seller.ad

import com.mohamedrejeb.calf.io.KmpFile
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL

internal actual suspend fun writeImageToCache(bytes: ByteArray, fileName: String): KmpFile? {
    val cacheDirectory = NSSearchPathForDirectoriesInDomains(
        directory = NSCachesDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String ?: return null

    val directory = "$cacheDirectory/screenshot_photos"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )

    val url = NSURL.fileURLWithPath(path = "$directory/$fileName", isDirectory = false)
    val written = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            .writeToURL(url, true)
    }
    if (!written) return null

    return KmpFile(url = url, originalUrl = url)
}
