package com.sirelon.sellsnap.features.seller.categories.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.sirelon.sellsnap.designsystem.AppChip
import com.sirelon.sellsnap.designsystem.AppChipDefaults
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.Cell
import com.sirelon.sellsnap.designsystem.Input
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeInputType
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttribute
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import com.sirelon.sellsnap.features.seller.categories.domain.ValidationError
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.attr_select_placeholder
import com.sirelon.sellsnap.generated.resources.cancel
import com.sirelon.sellsnap.generated.resources.confirm
import com.sirelon.sellsnap.generated.resources.error_attr_above_maximum
import com.sirelon.sellsnap.generated.resources.error_attr_below_minimum
import com.sirelon.sellsnap.generated.resources.error_attr_invalid_selection
import com.sirelon.sellsnap.generated.resources.error_attr_multiple_values_not_allowed
import com.sirelon.sellsnap.generated.resources.error_attr_must_be_numeric
import com.sirelon.sellsnap.generated.resources.error_attr_required
import com.sirelon.sellsnap.generated.resources.ic_chevron_right
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AttributeItem(
    attribute: OlxAttribute,
    selectedValues: List<OlxAttributeValue>,
    onSelectionChange: (List<OlxAttributeValue>) -> Unit,
    modifier: Modifier = Modifier,
    validationError: ValidationError? = null,
) {
    when (attribute.inputType) {
        AttributeInputType.SingleSelect -> AttributeSelectCell(
            attribute = attribute,
            selectedValues = selectedValues,
            multiSelect = false,
            onSelectionChange = onSelectionChange,
            validationError = validationError,
            modifier = modifier,
        )

        AttributeInputType.MultiSelect -> AttributeSelectCell(
            attribute = attribute,
            selectedValues = selectedValues,
            multiSelect = true,
            onSelectionChange = onSelectionChange,
            validationError = validationError,
            modifier = modifier,
        )

        AttributeInputType.NumericInput -> AttributeTextInputCell(
            attribute = attribute,
            selectedValues = selectedValues,
            onSelectionChange = onSelectionChange,
            keyboardType = KeyboardType.Number,
            validationError = validationError,
            modifier = modifier,
        )

        AttributeInputType.TextInput -> AttributeTextInputCell(
            attribute = attribute,
            selectedValues = selectedValues,
            onSelectionChange = onSelectionChange,
            keyboardType = KeyboardType.Text,
            validationError = validationError,
            modifier = modifier,
        )
    }
}

@Composable
private fun AttributeSelectCell(
    attribute: OlxAttribute,
    selectedValues: List<OlxAttributeValue>,
    multiSelect: Boolean,
    onSelectionChange: (List<OlxAttributeValue>) -> Unit,
    validationError: ValidationError?,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val label = buildAttributeLabel(attribute)
    val selectPlaceholder = stringResource(Res.string.attr_select_placeholder)

    Column(modifier = modifier) {
        Cell(
            transparent = true,
            overline = {
                Text(text = label)
            },
            headline = {
                Text(
                    text = if (selectedValues.isEmpty()) selectPlaceholder else selectedValues.joinToString { it.label },
                    color = if (selectedValues.isEmpty()) AppTheme.colors.onSurfaceMuted else AppTheme.colors.onSurface,
                )
            },
            trailing = {
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = AppTheme.colors.onSurfaceMuted,
                )
            },
            onClick = { showDialog = true },
        )

        if (multiSelect && selectedValues.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .padding(horizontal = AppDimens.Spacing.xl3)
                    .padding(bottom = AppDimens.Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
            ) {
                selectedValues.forEach { value ->
                    AppChip(
                        text = value.label,
                        enabled = true,
                        colors = AppChipDefaults.primaryColors(),
                    )
                }
            }
        }

        if (validationError != null) {
            Text(
                modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
                text = validationError.toMessage(),  // safe: called only when non-null
                style = AppTheme.typography.caption,
                color = AppTheme.colors.error,
            )
        }

        HorizontalDivider()
    }

    if (showDialog) {
        AttributeSelectDialog(
            title = attribute.label,
            options = attribute.allowedValues,
            selectedCodes = selectedValues.map { it.code }.toSet(),
            multiSelect = multiSelect,
            onConfirm = { selected ->
                onSelectionChange(selected)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun AttributeTextInputCell(
    attribute: OlxAttribute,
    selectedValues: List<OlxAttributeValue>,
    onSelectionChange: (List<OlxAttributeValue>) -> Unit,
    keyboardType: KeyboardType,
    validationError: ValidationError?,
    modifier: Modifier = Modifier,
) {
    var textValue by remember(attribute.code) {
        mutableStateOf(selectedValues.firstOrNull()?.label ?: "")
    }
    val label = buildAttributeLabel(attribute)

    val errorMessage = if (validationError != null) validationError.toMessage() else null
    val hintText = errorMessage ?: when {
        attribute.inputType == AttributeInputType.NumericInput -> buildString {
            attribute.validationRules.min?.let { append("Min: $it") }
            attribute.validationRules.max?.let {
                if (isNotEmpty()) append(", ")
                append("Max: $it")
            }
        }.takeIf { it.isNotEmpty() }

        else -> null
    }

    Input(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl3),
        value = textValue,
        onValueChange = { newValue ->
            textValue = newValue
            if (newValue.isNotBlank()) {
                onSelectionChange(
                    listOf(
                        OlxAttributeValue(
                            code = attribute.code,
                            label = newValue
                        )
                    )
                )
            } else {
                onSelectionChange(emptyList())
            }
        },
        label = label,
        supportingText = hintText,
        isError = validationError != null,
        singleLine = true,
        suffix = if (attribute.unit.isNotBlank()) {
            { Text(attribute.unit, style = AppTheme.typography.body) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun AttributeSelectDialog(
    title: String,
    options: List<OlxAttributeValue>,
    selectedCodes: Set<String>,
    multiSelect: Boolean,
    onConfirm: (List<OlxAttributeValue>) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingCodes by remember { mutableStateOf(selectedCodes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = AppTheme.typography.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (multiSelect) {
                                    pendingCodes = if (option.code in pendingCodes) {
                                        pendingCodes - option.code
                                    } else {
                                        pendingCodes + option.code
                                    }
                                } else {
                                    onConfirm(listOf(option))
                                }
                            }
                            .padding(vertical = AppDimens.Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (multiSelect) {
                            Checkbox(checked = option.code in pendingCodes, onCheckedChange = null)
                        } else {
                            RadioButton(selected = option.code in pendingCodes, onClick = null)
                        }
                        Text(
                            text = option.label,
                            style = AppTheme.typography.body,
                            modifier = Modifier.padding(start = AppDimens.Spacing.m),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (multiSelect) {
                TextButton(onClick = { onConfirm(options.filter { it.code in pendingCodes }) }) {
                    Text(stringResource(Res.string.confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

private fun buildAttributeLabel(attribute: OlxAttribute): String = buildString {
    append(attribute.label)
    if (attribute.unit.isNotBlank()) append(" (${attribute.unit})")
    if (attribute.validationRules.required) append(" *")
}

@Composable
private fun ValidationError.toMessage(): String = when (this) {
    ValidationError.Required -> stringResource(Res.string.error_attr_required)
    ValidationError.MustBeNumeric -> stringResource(Res.string.error_attr_must_be_numeric)
    is ValidationError.BelowMinimum -> stringResource(Res.string.error_attr_below_minimum, min)
    is ValidationError.AboveMaximum -> stringResource(Res.string.error_attr_above_maximum, max)
    is ValidationError.InvalidSelection -> stringResource(Res.string.error_attr_invalid_selection)
    ValidationError.MultipleValuesNotAllowed -> stringResource(Res.string.error_attr_multiple_values_not_allowed)
}
