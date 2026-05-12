package com.sirelon.sellsnap.features.seller.categories.domain

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class OlxAttribute(
    val code: String,
    val label: String,
    val unit: String,
    val inputType: AttributeInputType,
    val validationRules: AttributeValidationRules,
    val allowedValues: List<OlxAttributeValue>,
)

sealed interface AttributeInputType {
    data object SingleSelect : AttributeInputType
    data object MultiSelect : AttributeInputType
    data object NumericInput : AttributeInputType
    data object TextInput : AttributeInputType
}

@Immutable
data class AttributeValidationRules(
    val required: Boolean,
    val numeric: Boolean,
    val min: Double?,
    val max: Double?,
    val allowMultipleValues: Boolean,
)

@Immutable
@Serializable
data class OlxAttributeValue(
    val code: String,
    val label: String,
)
