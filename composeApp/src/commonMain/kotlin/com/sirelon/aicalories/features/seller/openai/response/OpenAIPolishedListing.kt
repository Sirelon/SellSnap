package com.sirelon.sellsnap.features.seller.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** SIR-86: a polish answer - the listing as the seller's instruction leaves it. */
@Serializable
internal class OpenAIPolishedListing(
    @SerialName("sameItem")
    val sameItem: Boolean = true,
    @SerialName("title")
    val title: String?,
    @SerialName("description")
    val description: String?,
    @SerialName("suggestedPrice")
    val suggestedPrice: Float?,
    @SerialName("minPrice")
    val minPrice: Float?,
    @SerialName("maxPrice")
    val maxPrice: Float?,
)
