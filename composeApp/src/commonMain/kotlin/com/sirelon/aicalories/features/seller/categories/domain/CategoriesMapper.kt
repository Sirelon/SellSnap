package com.sirelon.sellsnap.features.seller.categories.domain

import com.sirelon.sellsnap.features.seller.categories.data.response.OlxAttributeResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxAttributeValidationResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxAttributeValueResponse
import com.sirelon.sellsnap.features.seller.categories.data.response.OlxCategoryResponse

class CategoriesMapper {

    internal fun mapCategory(response: OlxCategoryResponse): OlxCategory? {
        val id = response.id ?: return null
        return OlxCategory(
            id = id,
            label = response.label.orEmpty(),
            parentId = response.parentId?.takeIf { it > 0 },
            isLeaf = response.isLeaf == true,
        )
    }

    internal fun mapAttributes(responses: List<OlxAttributeResponse>): List<OlxAttribute> =
        responses.mapNotNull { mapAttribute(it) }

    private fun mapAttribute(response: OlxAttributeResponse): OlxAttribute? {
        // code is the primary key — skip the attribute entirely if missing
        val code = response.code ?: return null
        val rules = mapValidationRules(response.validation)
        val allowedValues = response.values?.mapNotNull { mapValue(it) } ?: emptyList()
        return OlxAttribute(
            code = code,
            label = response.label ?: "",
            unit = response.unit ?: "",
            inputType = deriveInputType(allowedValues, rules),
            validationRules = rules,
            allowedValues = allowedValues,
        )
    }

    private fun mapValidationRules(response: OlxAttributeValidationResponse?): AttributeValidationRules =
        AttributeValidationRules(
            required = response?.required ?: false,
            numeric = response?.numeric ?: false,
            min = response?.min,
            max = response?.max,
            allowMultipleValues = response?.allowMultipleValues ?: false,
        )

    private fun deriveInputType(
        allowedValues: List<OlxAttributeValue>,
        rules: AttributeValidationRules,
    ): AttributeInputType = when {
        allowedValues.isNotEmpty() && rules.allowMultipleValues -> AttributeInputType.MultiSelect
        allowedValues.isNotEmpty() -> AttributeInputType.SingleSelect
        rules.numeric -> AttributeInputType.NumericInput
        else -> AttributeInputType.TextInput
    }

    private fun mapValue(response: OlxAttributeValueResponse): OlxAttributeValue? {
        val code = response.code ?: return null
        return OlxAttributeValue(
            code = code,
            label = response.label ?: "",
        )
    }

}
