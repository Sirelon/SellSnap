package com.sirelon.sellsnap.features.seller.categories.domain

import kotlinx.serialization.Serializable

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

data class AttributeValidationRules(
    val required: Boolean,
    val numeric: Boolean,
    val min: Double?,
    val max: Double?,
    val allowMultipleValues: Boolean,
)

@Serializable
data class OlxAttributeValue(
    val code: String,
    val label: String,
)
