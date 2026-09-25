package com.sirelon.sellsnap.features.media

import kotlin.test.Test
import kotlin.test.assertEquals

class UploadImageSizeTest {

    @Test
    fun `a landscape photo above the limit is clamped on its long side`() {
        assertEquals(
            ImageSize(width = 1600, height = 1200),
            downscaledImageSize(originalWidth = 4032, originalHeight = 3024),
        )
    }

    @Test
    fun `a portrait photo above the limit is clamped on its long side`() {
        assertEquals(
            ImageSize(width = 1200, height = 1600),
            downscaledImageSize(originalWidth = 3024, originalHeight = 4032),
        )
    }

    @Test
    fun `a photo already within the limit is never upscaled`() {
        assertEquals(
            ImageSize(width = 800, height = 600),
            downscaledImageSize(originalWidth = 800, originalHeight = 600),
        )
    }

    @Test
    fun `a photo exactly at the limit is returned unchanged`() {
        assertEquals(
            ImageSize(width = 1600, height = 900),
            downscaledImageSize(originalWidth = 1600, originalHeight = 900),
        )
    }

    @Test
    fun `a square photo above the limit is clamped to a square`() {
        assertEquals(
            ImageSize(width = 1600, height = 1600),
            downscaledImageSize(originalWidth = 2000, originalHeight = 2000),
        )
    }

    @Test
    fun `a custom limit is honored`() {
        assertEquals(
            ImageSize(width = 800, height = 600),
            downscaledImageSize(originalWidth = 1600, originalHeight = 1200, maxLongSidePx = 800),
        )
    }
}
