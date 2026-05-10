package com.sirelon.sellsnap.features.seller.ad.data

import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.openai.response.OpenAIGeneratedAd

internal class GeneratedAdMapper {

    fun mapToDomain(generatedAd: OpenAIGeneratedAd, images: List<String>): Advertisement {
        val suggestedPrice = generatedAd.suggestedPrice ?: 0f
        val minPrice = generatedAd.minPrice ?: suggestedPrice
        val maxPrice = generatedAd.maxPrice ?: suggestedPrice
        val normalizedMinPrice = minOf(
            minPrice,
            maxPrice,
            suggestedPrice,
        ).coerceAtLeast(0f)

        val normalizedMaxPrice = maxOf(
            minPrice,
            maxPrice,
            suggestedPrice,
        ).coerceAtLeast(normalizedMinPrice)

        val normalizedSuggestedPrice = suggestedPrice
            .coerceAtLeast(0f)
            .coerceIn(normalizedMinPrice, normalizedMaxPrice)

        return Advertisement(
            title = generatedAd.title.orEmpty().trim().ifBlank { "Товар" },
            description = generatedAd.description.orEmpty().trim().ifBlank { "Стан і деталі дивіться на фото." },
            suggestedPrice = normalizedSuggestedPrice,
            minPrice = normalizedMinPrice,
            maxPrice = normalizedMaxPrice,
            images = images.distinct(),
        )
    }
}
