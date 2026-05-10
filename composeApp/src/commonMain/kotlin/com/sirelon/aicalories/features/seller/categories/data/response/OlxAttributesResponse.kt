package com.sirelon.sellsnap.features.seller.categories.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxAttributesResponse(
    @SerialName("data") val data: List<OlxAttributeResponse>?,
)

@Serializable
internal class OlxAttributeResponse(
    @SerialName("code") val code: String?,
    @SerialName("label") val label: String?,
    @SerialName("unit") val unit: String?,
    @SerialName("validation") val validation: OlxAttributeValidationResponse?,
    @SerialName("values") val values: List<OlxAttributeValueResponse>?,
)

@Serializable
internal class OlxAttributeValidationResponse(
    @SerialName("type") val type: String?,
    @SerialName("required") val required: Boolean?,
    @SerialName("numeric") val numeric: Boolean?,
    @SerialName("min") val min: Double?,
    @SerialName("max") val max: Double?,
    @SerialName("allow_multiple_values") val allowMultipleValues: Boolean?,
)

@Serializable
internal class OlxAttributeValueResponse(
    @SerialName("code") val code: String?,
    @SerialName("label") val label: String?,
)
