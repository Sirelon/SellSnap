package com.sirelon.sellsnap.features.seller.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OpenAIListingContextResponse(
    @SerialName("itemType")
    val itemType: String?,
    @SerialName("sellerFacts")
    val sellerFacts: OpenAISellerFactsResponse?,
    @SerialName("mustUsePhrases")
    val mustUsePhrases: List<String>?,
    @SerialName("visualFacts")
    val visualFacts: List<String>?,
)

@Serializable
internal class OpenAISellerFactsResponse(
    @SerialName("brand")
    val brand: String?,
    @SerialName("model")
    val model: String?,
    @SerialName("size")
    val size: String?,
    @SerialName("purchaseAge")
    val purchaseAge: String?,
    @SerialName("condition")
    val condition: String?,
    @SerialName("season")
    val season: String?,
    @SerialName("material")
    val material: String?,
)
