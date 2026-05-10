package com.sirelon.sellsnap.features.seller.openai.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OpenAIAttributesRequest(
    @SerialName("attributes")
    val attributes: List<OpenAIAttributeRequest>,
)

@Serializable
internal class OpenAIAttributeRequest(
    @SerialName("code")
    val code: String,
    @SerialName("label")
    val label: String,
    @SerialName("type")
    val type: String,
    @SerialName("required")
    val required: Boolean? = null,
    @SerialName("options")
    val options: List<OpenAIAttributeOptionRequest>? = null,
    @SerialName("min")
    val min: Double? = null,
    @SerialName("max")
    val max: Double? = null,
    @SerialName("unit")
    val unit: String? = null,
)

@Serializable
internal class OpenAIAttributeOptionRequest(
    @SerialName("code")
    val code: String,
    @SerialName("label")
    val label: String,
)
