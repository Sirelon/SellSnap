package com.sirelon.sellsnap.features.seller.ad.data

import com.sirelon.sellsnap.features.seller.ad.Advertisement
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.openai.response.OpenAIGeneratedAd
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The model answered with something that is not a publishable listing. [missing] names the fields
 * for the crash report; the seller sees the ordinary generate-failed message and can try again.
 */
internal class IncompleteGeneratedAdException(val missing: List<String>) :
    IllegalStateException("Generated ad is missing: " + missing.joinToString()) {

    /** No title or no price: the model answered with nothing worth showing, as opposed to a listing
     * that merely lacks a description. Logged as `empty_output` in `ad_generation_failed`. */
    val isEmptyOutput: Boolean get() = "title" in missing || "suggestedPrice" in missing
}

internal class GeneratedAdMapper {

    /**
     * @throws IncompleteGeneratedAdException when the model left out the title, the description or
     * the price, or answered a price of zero or less (a 2026-09-25 generation came back with an
     * empty title and price 0 and was shown as a success). Standing a placeholder in for any of them is worse than failing: it reads as a
     * real listing, it is written in one fixed language whatever market the seller is in, and it
     * publishes to OLX under the seller's name. A failure the seller can retry beats that.
     */
    fun mapToDomain(generatedAd: OpenAIGeneratedAd, images: List<String>): Advertisement {
        val title = generatedAd.title.orEmpty().trim()
        val description = generatedAd.description.orEmpty().trim()

        val missing = buildList {
            if (title.isBlank()) add("title")
            if (description.isBlank()) add("description")
            if (generatedAd.suggestedPrice == null || generatedAd.suggestedPrice <= 0f) add("suggestedPrice")
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

/**
 * Rounds the model's three prices to [country]'s natural step ([OlxCountry.priceRoundingStep]):
 * the suggested price to the nearest step (half up), the minimum down and the maximum up, so the
 * range only ever widens and min <= suggested <= max still holds. A positive price never rounds
 * to zero, since a listing priced at nothing would go out free.
 */
internal fun Advertisement.roundedToMarketSteps(country: OlxCountry): Advertisement {
    fun round(price: Float, toStep: (Float) -> Float): Float {
        if (price <= 0f) return price
        val step = country.priceRoundingStep(price).toFloat()
        return (toStep(price / step) * step).coerceAtLeast(step)
    }

    val roundedMin = round(minPrice) { floor(it) }
    val roundedMax = round(maxPrice) { ceil(it) }.coerceAtLeast(roundedMin)
    val roundedSuggested = round(suggestedPrice) { floor(it + 0.5f) }.coerceIn(roundedMin, roundedMax)
    return copy(suggestedPrice = roundedSuggested, minPrice = roundedMin, maxPrice = roundedMax)
}
