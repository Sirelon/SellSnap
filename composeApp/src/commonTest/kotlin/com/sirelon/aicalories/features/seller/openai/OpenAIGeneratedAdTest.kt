package com.sirelon.sellsnap.features.seller.openai

import com.sirelon.sellsnap.features.seller.openai.response.OpenAIGeneratedAd
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A refusal payload carries none of the listing keys, so decoding it rests on `explicitNulls =
 * false` on the app's Json (see KoinModules), which reads a missing nullable property as null.
 * Turn that flag off and every refusal throws MissingFieldException instead of reaching the
 * seller, which is why the flag is pinned here rather than left to be rediscovered.
 */
class OpenAIGeneratedAdTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun refusalPayloadDecodesWithoutListingFields() {
        val decoded = json.decodeFromString<OpenAIGeneratedAd>("""{"unrecognized":"too_blurry"}""")

        assertEquals("too_blurry", decoded.unrecognized)
        assertNull(decoded.title)
        assertNull(decoded.description)
        assertNull(decoded.suggestedPrice)
    }

    @Test
    fun listingPayloadDecodesWithoutRefusalField() {
        val decoded = json.decodeFromString<OpenAIGeneratedAd>(
            """{"title":"Кеди Superga","description":"Стан гарний.","suggestedPrice":900,"minPrice":700,"maxPrice":1100}"""
        )

        assertNull(decoded.unrecognized)
        assertEquals("Кеди Superga", decoded.title)
        assertEquals(900f, decoded.suggestedPrice)
    }

    @Test
    fun everyReasonCodeThePromptListsMapsToAReason() {
        val codes = listOf("too_blurry", "too_dark", "not_an_item", "different_items")

        assertEquals(codes, codes.mapNotNull { UnusablePhotoReason.from(it)?.code })
    }

    @Test
    fun unknownReasonCodeMapsToNull() {
        assertNull(UnusablePhotoReason.from("wrong_colour"))
    }
}
