package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PlatformImeOptions

@Composable
internal fun rememberKeyboardDismissAction(): () -> Unit {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    return remember(keyboardController, focusManager) {
        {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
}

internal fun Modifier.dismissKeyboardOnTapOutside(onDismiss: () -> Unit): Modifier =
    pointerInput(onDismiss) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
            if (up != null && !down.isConsumed && !up.isConsumed) {
                onDismiss()
            }
        }
    }

internal fun KeyboardOptions.withKeyboardDismissDefaults(
    platformImeOptions: PlatformImeOptions?,
): KeyboardOptions =
    copy(
        imeAction = when (imeAction) {
            ImeAction.Default,
            ImeAction.Unspecified -> ImeAction.Done
            else -> imeAction
        },
        platformImeOptions = this.platformImeOptions ?: platformImeOptions,
        showKeyboardOnFocus = showKeyboardOnFocus,
        hintLocales = hintLocales,
    )

@Composable
internal expect fun rememberPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit,
): PlatformImeOptions?
