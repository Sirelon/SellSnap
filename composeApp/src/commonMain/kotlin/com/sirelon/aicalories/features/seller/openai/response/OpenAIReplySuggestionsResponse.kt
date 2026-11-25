package com.sirelon.sellsnap.features.seller.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The model's answer to the reply-suggestions prompt (SIR-96). */
@Serializable
internal class OpenAIReplySuggestionsResponse(
    @SerialName("replies")
    val replies: List<OpenAIReplySuggestionResponse>?,
)

@Serializable
internal class OpenAIReplySuggestionResponse(
    @SerialName("text")
    val text: String?,
    @SerialName("needs_review")
    val needsReview: Boolean?,
    /** A number; the model may write `1500.0`, so it is read as a double. */
    @SerialName("price")
    val price: Double?,
)
