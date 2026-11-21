package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PlatformImeOptions
import kotlin.math.abs

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

/**
 * Closes the keyboard when the seller drags the scrollable it is attached to with
 * `Modifier.nestedScroll(...)`, e.g. a message list above a reply field.
 */
@Composable
internal fun rememberDismissKeyboardOnDragConnection(onDismiss: () -> Unit): NestedScrollConnection {
    val latestOnDismiss = rememberUpdatedState(onDismiss)
    return remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available != Offset.Zero) latestOnDismiss.value()
                return Offset.Zero
            }
        }
    }
}

/**
 * Closes the keyboard when the seller drags vertically on the content, e.g. scrolls a form.
 *
 * Watches the finger, not the scroll: Compose reports the scroll that brings a focused field into
 * view as `NestedScrollSource.UserInput` (foundation 1.12 `ContentInViewNode`), so
 * [rememberDismissKeyboardOnDragConnection] would close the keyboard the moment it opens over a
 * field inside the scrolled content.
 */
internal fun Modifier.dismissKeyboardOnVerticalDrag(onDismiss: () -> Unit): Modifier =
    pointerInput(onDismiss) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var drag = Offset.Zero
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                drag += change.position - change.previousPosition
                if (abs(drag.y) > viewConfiguration.touchSlop && abs(drag.y) > abs(drag.x)) {
                    onDismiss()
                    break
                }
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

/**
 * iOS: native text input with a "Done" bar above the keyboard, or without the bar when
 * [doneLabel] is null. Other platforms: null.
 */
@Composable
internal expect fun rememberPlatformImeOptions(
    doneLabel: String?,
    onDone: () -> Unit,
): PlatformImeOptions?
