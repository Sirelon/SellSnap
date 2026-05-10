package com.sirelon.sellsnap.features.seller.categories.domain

sealed interface AttributeValidationResult {
    data object Valid : AttributeValidationResult
    data class Invalid(val reason: ValidationError) : AttributeValidationResult
}

sealed interface ValidationError {
    data object Required : ValidationError
    data object MustBeNumeric : ValidationError
    data class BelowMinimum(val min: Double) : ValidationError
    data class AboveMaximum(val max: Double) : ValidationError
    data class InvalidSelection(val invalidCodes: List<String>) : ValidationError
    data object MultipleValuesNotAllowed : ValidationError
}

class AttributeValidator {

    fun validate(
        attribute: OlxAttribute,
        selectedValues: List<String>,
    ): AttributeValidationResult {
        val rules = attribute.validationRules

        if (selectedValues.isEmpty()) {
            return if (rules.required) {
                AttributeValidationResult.Invalid(ValidationError.Required)
            } else {
                AttributeValidationResult.Valid
            }
        }

        if (!rules.allowMultipleValues && selectedValues.size > 1) {
            return AttributeValidationResult.Invalid(ValidationError.MultipleValuesNotAllowed)
        }

        if (attribute.allowedValues.isNotEmpty()) {
            val allowedCodes = attribute.allowedValues.map { it.code }.toSet()
            val invalidCodes = selectedValues.filter { it !in allowedCodes }
            if (invalidCodes.isNotEmpty()) {
                return AttributeValidationResult.Invalid(ValidationError.InvalidSelection(invalidCodes))
            }
        }

        if (rules.numeric) {
            for (value in selectedValues) {
                val parsed = value.toDoubleOrNull()
                    ?: return AttributeValidationResult.Invalid(ValidationError.MustBeNumeric)

                rules.min?.let { min ->
                    if (parsed < min) return AttributeValidationResult.Invalid(ValidationError.BelowMinimum(min))
                }
                rules.max?.let { max ->
                    if (parsed > max) return AttributeValidationResult.Invalid(ValidationError.AboveMaximum(max))
                }
            }
        }

        return AttributeValidationResult.Valid
    }

    fun validateAll(
        attributes: List<OlxAttribute>,
        selections: Map<String, List<String>>,
    ): Map<String, AttributeValidationResult> =
        attributes.associate { attribute ->
            attribute.code to validate(attribute, selections[attribute.code] ?: emptyList())
        }
}
