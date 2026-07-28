package com.sirelon.sellsnap.features.seller.ad

import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.generated.resources.Res

/**
 * Bundled test photos used to seed the GenerateAd screen when [screenshotMode] is on,
 * so screenshot runs never have to drive the OS photo picker.
 *
 * The picker cannot be automated reliably. On iPad, iOS 26 presents
 * PHPickerViewController as a popover that exposes none of its contents to the
 * accessibility tree — only a PopoverDismissRegion — so there is nothing to select
 * (verified on iPad Pro 13-inch, iOS 26.5). Android additionally needs different
 * selectors depending on which picker implementation the device ships.
 */
private val ScreenshotPhotoResources = listOf(
    "files/screenshot_photo_1.jpg",
    "files/screenshot_photo_2.jpg",
    "files/screenshot_photo_3.jpg",
)

/**
 * Writes [bytes] to a cache file and wraps it so the result can be fed into the normal
 * photo pipeline. Returns null when the platform has no writable cache (web).
 */
internal expect suspend fun writeImageToCache(bytes: ByteArray, fileName: String): KmpFile?

/**
 * Materialises the bundled test photos as files the upload pipeline can read.
 * The caller dispatches these exactly as if the user had picked them, so preparation,
 * persistence and upload all run unchanged.
 */
internal suspend fun loadScreenshotPhotos(): List<KmpFile> =
    ScreenshotPhotoResources.mapIndexedNotNull { index, path ->
        runCatching {
            writeImageToCache(
                bytes = Res.readBytes(path),
                fileName = "screenshot_photo_${index + 1}.jpg",
            )
        }.getOrNull()
    }
