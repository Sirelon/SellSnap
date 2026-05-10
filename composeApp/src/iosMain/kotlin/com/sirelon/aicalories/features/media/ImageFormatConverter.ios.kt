package com.sirelon.sellsnap.features.media

import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.getName
import com.mohamedrejeb.calf.io.readByteArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

actual fun imageFormatConverter(): ImageFormatConverter = IosImageFormatConverter()

private class IosImageFormatConverter : ImageFormatConverter {

    override suspend fun convert(file: KmpFile): KmpFile {
        return withContext(Dispatchers.Main) {
            val currentName = file.getName()
            if (!currentName.isHeic()) {
                return@withContext file
            }

            val imageData = runCatching {
                file.readByteArray()
            }.getOrNull()
                ?: return@withContext file

            val uiImage = UIImage(data = imageData.toNSData())

            val jpegData = UIImageJPEGRepresentation(uiImage, 0.9)
                ?: return@withContext file

            val destinationName = currentName.toJpegFileName()
            val destinationUrl =
                NSURL.fileURLWithPath(
                    path = NSTemporaryDirectory() + destinationName,
                    isDirectory = false,
                )

            val success = jpegData.writeToURL(destinationUrl, true)
            if (success) {
                KmpFile(url = destinationUrl, originalUrl = destinationUrl)
            } else {
                file
            }
        }
    }
}

private fun String?.isHeic(): Boolean {
    if (this == null) return false
    val lower = lowercase()
    return lower.endsWith(".heic") || lower.endsWith(".heif")
}

private fun String?.toJpegFileName(): String {
    val base = this?.substringBeforeLast('.', missingDelimiterValue = this ?: "") ?: ""
    val safeBase = base.ifBlank { "image_${NSUUID().UUIDString}" }
    return "$safeBase.jpg"
}

private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(
            bytes = pinned.addressOf(0),
            length = size.toULong(),
        )
    }
