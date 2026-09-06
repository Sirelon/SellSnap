package com.sirelon.sellsnap.features.seller.ad.data

import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.openai.response.OpenAIGeneratedAd

/**
 * The model answered with something that is not a publishable listing. [missing] names the fields
 * for the crash report; the seller sees the ordinary generate-failed message and can try again.
 */
internal class IncompleteGeneratedAdException(val missing: List<String>) :
    IllegalStateException("Generated ad is missing: " + missing.joinToString())

internal class GeneratedAdMapper {

    /**
     * @throws IncompleteGeneratedAdException when the model left out the title, the description or
     * the price. Standing a placeholder in for any of them is worse than failing: it reads as a
     * real listing, it is written in one fixed language whatever market the seller is in, and it
     * publishes to OLX under the seller's name. A failure the seller can retry beats that.
     */
    fun mapToDomain(generatedAd: OpenAIGeneratedAd, images: List<String>): Advertisement {
        val title = generatedAd.title.orEmpty().trim()
        val description = generatedAd.description.orEmpty().trim()

        val missing = buildList {
            if (title.isBlank()) add("title")
            if (description.isBlank()) add("description")
            if (generatedAd.suggestedPrice == null) add("suggestedPrice")
        }
        if (missing.isNotEmpty()) throw IncompleteGeneratedAdException(missing)

        // Reported as missing above when absent, so this cannot fail here.
        val suggestedPrice = checkNotNull(generatedAd.suggestedPrice)
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
            title = title,
            description = description,
            suggestedPrice = normalizedSuggestedPrice,
            minPrice = normalizedMinPrice,
            maxPrice = normalizedMaxPrice,
            images = images.distinct(),
        )
    }
}
