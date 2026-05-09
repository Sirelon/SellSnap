package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.character_count_range
import com.sirelon.sellsnap.generated.resources.min_characters
import org.jetbrains.compose.resources.stringResource

@Composable
fun Input(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    minCharacters: Int = -1,
    maxCharacters: Int = -1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val focusManager = LocalFocusManager.current
    val resolvedKeyboardOptions = if (keyboardOptions.imeAction == ImeAction.Default) {
        keyboardOptions.copy(imeAction = ImeAction.Done)
    } else {
        keyboardOptions
    }
    val resolvedKeyboardActions = if (keyboardActions == KeyboardActions.Default) {
        KeyboardActions(onDone = { focusManager.clearFocus(force = true) })
    } else {
        keyboardActions
    }
    val characterCountText = when {
        minCharacters > 0 && value.length < minCharacters -> stringResource(
            Res.string.min_characters,
            minCharacters
        )

        maxCharacters > 0 -> stringResource(
            Res.string.character_count_range,
            value.length,
            maxCharacters
        )

        else -> supportingText
    }

    // Filled variant per design spec: surface-low background, no border,
    // 2px primary indicator on focus only
    TextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AppTheme.colors.surfaceLow,
            unfocusedContainerColor = AppTheme.colors.surfaceLow,
            disabledContainerColor = AppTheme.colors.surfaceLow.copy(alpha = 0.6f),
            errorContainerColor = AppTheme.colors.surfaceLow,
            focusedIndicatorColor = AppTheme.colors.primary,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = AppTheme.colors.error,
            focusedTextColor = AppTheme.colors.onSurface,
            unfocusedTextColor = AppTheme.colors.onSurface,
            focusedLabelColor = AppTheme.colors.primary,
            unfocusedLabelColor = AppTheme.colors.onSurface.copy(alpha = 0.6f),
            cursorColor = AppTheme.colors.primary,
        ),
        label = label?.let {
            {
                Text(
                    text = it,
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
        },
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.onSurface.copy(alpha = 0.5f),
                )
            }
        },
        supportingText = characterCountText?.let {
            {
                Text(
                    text = it,
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        suffix = suffix,
        prefix = prefix,
        isError = isError,
        singleLine = true,
        minLines = 1,
        maxLines = 1,
        keyboardOptions = resolvedKeyboardOptions,
        keyboardActions = resolvedKeyboardActions,
        visualTransformation = visualTransformation,
        textStyle = AppTheme.typography.body,
    )
}

@Composable
fun TransparentInput(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    minCharacters: Int = -1,
    maxCharacters: Int = Int.MIN_VALUE,
    prefix: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
) {
    val focusManager = LocalFocusManager.current
    val resolvedKeyboardOptions = if (keyboardOptions.imeAction == ImeAction.Default) {
        keyboardOptions.copy(imeAction = ImeAction.Done)
    } else {
        keyboardOptions
    }
    val keyboardActionHandler = KeyboardActionHandler { performDefaultAction ->
        focusManager.clearFocus(force = true)
        performDefaultAction()
    }
    val characterCountText = when {
        minCharacters > 0 && state.text.length < minCharacters -> stringResource(
            Res.string.min_characters,
            minCharacters
        )

        maxCharacters > 0 -> stringResource(
            Res.string.character_count_range,
            state.text.length,
            maxCharacters
        )

        else -> null
    }

    TextField(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isError) {
                    Modifier.border(
                        width = AppDimens.BorderWidth.m,
                        color = AppTheme.colors.error,
                        shape = TextFieldDefaults.shape,
                    )
                } else {
                    Modifier
                }
            ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = AppTheme.colors.primary,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = AppTheme.colors.error,
        ),
        state = state,
        prefix = prefix,
        keyboardOptions = resolvedKeyboardOptions,
        onKeyboardAction = keyboardActionHandler,
        lineLimits = lineLimits,
        supportingText = characterCountText?.let {
            {
                Text(text = it)
            }
        },
        isError = isError,
        outputTransformation = outputTransformation,
        inputTransformation = inputTransformation,
    )
}
