package com.sirelon.sellsnap.features.seller.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OpenAIGeneratedAd(
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
