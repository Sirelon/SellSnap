package com.sirelon.sellsnap.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.input.PlatformImeOptions
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIScreen
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleLeftMargin
import platform.UIKit.UIViewAutoresizingFlexibleWidth

private const val KeyboardAccessoryHeight = 44.0
private const val DoneButtonHorizontalInset = 8.0
private const val DoneButtonWidth = 88.0

@Composable
internal actual fun rememberPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit,
): PlatformImeOptions? {
    val latestOnDone = rememberUpdatedState(onDone)
    return remember(doneLabel) {
        PlatformImeOptions {
            usingNativeTextInput(true)
            inputAccessoryView(
                createKeyboardDoneAccessoryView(doneLabel) {
                    latestOnDone.value()
                }
            )
        }
    }
}

private fun createKeyboardDoneAccessoryView(
    doneLabel: String,
    onDone: () -> Unit,
): UIView {
    val width = UIScreen.mainScreen.bounds.useContents { size.width }
    val accessoryView = UIView(
        frame = CGRectMake(
            x = 0.0,
            y = 0.0,
            width = width,
            height = KeyboardAccessoryHeight,
        )
    )
    accessoryView.autoresizingMask = UIViewAutoresizingFlexibleWidth
    accessoryView.backgroundColor = UIColor.whiteColor

    val doneButton = UIButton.buttonWithType(UIButtonTypeSystem)
    doneButton.setTitle(doneLabel, forState = UIControlStateNormal)
    doneButton.setFrame(
        frame = CGRectMake(
            x = width - DoneButtonWidth - DoneButtonHorizontalInset,
            y = 0.0,
            width = DoneButtonWidth,
            height = KeyboardAccessoryHeight,
        )
    )
    doneButton.autoresizingMask = UIViewAutoresizingFlexibleLeftMargin
    doneButton.addAction(
        action = UIAction.actionWithHandler { onDone() },
        forControlEvents = UIControlEventTouchUpInside,
    )
    accessoryView.addSubview(doneButton)

    return accessoryView
}
