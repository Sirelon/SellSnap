package com.sirelon.sellsnap.features.media

import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.readByteArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill

actual fun imageFormatConverter(): ImageFormatConverter = IosImageFormatConverter()

/** Upload quality; see [MAX_UPLOAD_IMAGE_LONG_SIDE_PX] for the bench behind it. */
private const val JPEG_QUALITY = 0.75

private class IosImageFormatConverter : ImageFormatConverter {

    override suspend fun convert(file: KmpFile): KmpFile {
        return withContext(Dispatchers.Default) {
            val imageData = runCatching {
                file.readByteArray()
            }.getOrNull()
                ?: return@withContext file

            val uiImage = UIImage(data = imageData.toNSData())

            val originalSize = uiImage.size.useContents { width to height }
            val targetSize = downscaledImageSize(
                originalWidth = originalSize.first.toInt(),
                originalHeight = originalSize.second.toInt(),
            )
            val targetRect = CGRectMake(0.0, 0.0, targetSize.width.toDouble(), targetSize.height.toDouble())

            // UIImage.drawInRect respects imageOrientation (EXIF), so rendering through it here
            // both resizes and bakes in the correct upright orientation in one pass. JPEG has no
            // alpha channel, so a white fill first keeps a transparent PNG/WEBP source from
            // encoding as black where it used to be transparent.
            //
            // The renderer's default format draws at the main screen's scale, so on a 3x iPhone a
            // 1600-point canvas became a 4800-pixel JPEG of ~6 MB: OLX refused it ("max 5 MB") and
            // OpenAI timed out downloading it (SIR-205). Scale 1 makes points equal pixels.
            val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
            val renderer = UIGraphicsImageRenderer(
                size = CGSizeMake(targetSize.width.toDouble(), targetSize.height.toDouble()),
                format = format,
            )
            val resizedImage = renderer.imageWithActions { _ ->
                UIColor.whiteColor.setFill()
                UIRectFill(targetRect)
                uiImage.drawInRect(targetRect)
            }

            val jpegData = UIImageJPEGRepresentation(resizedImage, JPEG_QUALITY)
                ?: return@withContext file

            // Every converted file gets its own random name: two source files sharing a base name
            // (e.g. two same-named exports in one picker batch) must not overwrite each other's
            // temp file before upload.
            val destinationUrl =
                NSURL.fileURLWithPath(
                    path = NSTemporaryDirectory() + "upload_${NSUUID().UUIDString}.jpg",
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

private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(
            bytes = pinned.addressOf(0),
            length = size.toULong(),
        )
    }
