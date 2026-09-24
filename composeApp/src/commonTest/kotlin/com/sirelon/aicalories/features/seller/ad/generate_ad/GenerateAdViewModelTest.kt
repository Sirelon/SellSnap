package com.sirelon.sellsnap.features.seller.ad.generate_ad

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateAdViewModelTest {

    @Test
    fun `a succeeded run carries every finished stage`() {
        val params = buildGenerationStageParams(
            durationMs = 5_000L,
            photoCount = 3,
            uploadMs = 1_000L,
            uploadBytes = 2_400_000L,
            modelMs = 3_200L,
            attributesMs = 700L,
        )

        assertEquals(5_000L, params["duration_ms"])
        assertEquals(3, params["photo_count"])
        assertEquals(1_000L, params["upload_ms"])
        assertEquals(2_400_000L, params["upload_bytes"])
        assertEquals(3_200L, params["model_ms"])
        assertEquals(700L, params["attributes_ms"])
    }

    @Test
    fun `a guest run never carries attributes_ms`() {
        val params = buildGenerationStageParams(
            durationMs = 4_000L,
            photoCount = 2,
            uploadMs = 800L,
            uploadBytes = 1_600_000L,
            modelMs = 2_500L,
            attributesMs = null,
        )

        assertFalse(params.containsKey("attributes_ms"), "guest flow never fills attributes, so the param must be absent, not zero")
        assertTrue(params.containsKey("upload_ms"))
        assertTrue(params.containsKey("model_ms"))
    }

    @Test
    fun `a run that failed during upload carries only duration_ms and photo_count`() {
        val params = buildGenerationStageParams(
            durationMs = 900L,
            photoCount = 3,
            uploadMs = null,
            uploadBytes = null,
            modelMs = null,
            attributesMs = null,
        )

        assertEquals(setOf("duration_ms", "photo_count"), params.keys)
    }

    @Test
    fun `a run abandoned mid-model-call carries the finished upload stage only`() {
        val params = buildGenerationStageParams(
            durationMs = 6_000L,
            photoCount = 1,
            uploadMs = 1_200L,
            uploadBytes = 900_000L,
            modelMs = null,
            attributesMs = null,
        )

        assertEquals(setOf("duration_ms", "photo_count", "upload_ms", "upload_bytes"), params.keys)
    }
}
