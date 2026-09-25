package com.sirelon.sellsnap.features.media

import kotlin.math.roundToInt

/**
 * OpenAI's "high" image detail mode scales an image down so its short side is 768px before the
 * model ever sees it (platform.openai.com/docs/guides/vision, "Calculating costs"). Capping the
 * long side at this value before upload keeps strictly more detail than OpenAI itself uses, while
 * cutting upload size and generation latency for the seller.
 */
const val MAX_UPLOAD_IMAGE_LONG_SIDE_PX = 1600

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
