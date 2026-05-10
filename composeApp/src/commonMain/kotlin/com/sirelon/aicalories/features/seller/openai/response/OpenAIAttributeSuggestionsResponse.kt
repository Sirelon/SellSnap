package com.sirelon.sellsnap.features.seller.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OpenAIAttributeSuggestionsResponse(
    @SerialName("attributes")
    val attributes: List<OpenAIAttributeSuggestionResponse>?,
)

@Serializable
internal class OpenAIAttributeSuggestionResponse(
    @SerialName("code")
    val code: String?,
    @SerialName("valueCodes")
    val valueCodes: List<String>?,
    @SerialName("valueText")
    val valueText: String?,
    @SerialName("confidence")
    val confidence: String?,
)
