package com.sirelon.sellsnap.features.media

import kotlin.math.roundToInt

/**
 * Long side of every photo the app uploads, together with JPEG quality 75 in the platform
 * converters. The listing model reads 1280 px photos as well as 1600 px ones: in the SIR-174 bench
 * (`research/MODEL-BENCH-listing-generation.md`, "Upload size") both sizes named the same item on
 * all 25 photos, part numbers included, while 1280 px at quality 75 is about 40% fewer bytes to
 * upload and 21% fewer input tokens.
 */
const val MAX_UPLOAD_IMAGE_LONG_SIDE_PX = 1280

data class ImageSize(val width: Int, val height: Int)

/**
 * Scales [originalWidth]x[originalHeight] down so its long side is at most [maxLongSidePx],
 * preserving aspect ratio. Never upscales: an image already within the limit is returned as-is.
 */
fun downscaledImageSize(
    originalWidth: Int,
    originalHeight: Int,
    maxLongSidePx: Int = MAX_UPLOAD_IMAGE_LONG_SIDE_PX,
): ImageSize {
    val longSide = maxOf(originalWidth, originalHeight)
    if (longSide <= maxLongSidePx) {
        return ImageSize(originalWidth, originalHeight)
    }

    val scale = maxLongSidePx.toDouble() / longSide
    return ImageSize(
        width = (originalWidth * scale).roundToInt().coerceAtLeast(1),
        height = (originalHeight * scale).roundToInt().coerceAtLeast(1),
    )
}
