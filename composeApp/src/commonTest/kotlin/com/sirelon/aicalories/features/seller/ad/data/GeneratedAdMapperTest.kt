package com.sirelon.sellsnap.features.seller.ad.data

import com.sirelon.sellsnap.features.seller.openai.response.OpenAIGeneratedAd
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GeneratedAdMapperTest {

    private val mapper = GeneratedAdMapper()
    private val images = listOf("https://example.com/a.jpg")

    private fun ad(
        title: String? = "Кеди Superga",
        description: String? = "Стан гарний, підошва світла.",
        suggestedPrice: Float? = 900f,
        minPrice: Float? = 700f,
        maxPrice: Float? = 1100f,
    ) = OpenAIGeneratedAd(title, description, suggestedPrice, minPrice, maxPrice)

    @Test
    fun completeAdMapsThrough() {
        val advertisement = mapper.mapToDomain(ad(), images)

        assertEquals("Кеди Superga", advertisement.title)
        assertEquals(900f, advertisement.suggestedPrice)
        assertEquals(700f, advertisement.minPrice)
        assertEquals(1100f, advertisement.maxPrice)
    }

    @Test
    fun blankTitleFailsInsteadOfUsingAPlaceholder() {
        val error = assertFailsWith<IncompleteGeneratedAdException> {
            mapper.mapToDomain(ad(title = "   "), images)
        }

        assertEquals(listOf("title"), error.missing)
    }

    @Test
    fun missingDescriptionFailsInsteadOfUsingAPlaceholder() {
        val error = assertFailsWith<IncompleteGeneratedAdException> {
            mapper.mapToDomain(ad(description = null), images)
        }

        assertEquals(listOf("description"), error.missing)
    }

    @Test
    fun missingPriceFailsRatherThanPricingTheListingAtZero() {
        val error = assertFailsWith<IncompleteGeneratedAdException> {
            mapper.mapToDomain(ad(suggestedPrice = null), images)
        }

        assertEquals(listOf("suggestedPrice"), error.missing)
    }

    @Test
    fun everyMissingFieldIsReportedTogether() {
        val error = assertFailsWith<IncompleteGeneratedAdException> {
            mapper.mapToDomain(ad(title = null, description = "", suggestedPrice = null), images)
        }

        assertEquals(listOf("title", "description", "suggestedPrice"), error.missing)
    }

    @Test
    fun outOfOrderPricesAreNormalized() {
        val advertisement = mapper.mapToDomain(
            ad(suggestedPrice = 900f, minPrice = 1200f, maxPrice = 400f),
            images,
        )

        assertTrue(advertisement.minPrice <= advertisement.suggestedPrice)
        assertTrue(advertisement.suggestedPrice <= advertisement.maxPrice)
    }
}
