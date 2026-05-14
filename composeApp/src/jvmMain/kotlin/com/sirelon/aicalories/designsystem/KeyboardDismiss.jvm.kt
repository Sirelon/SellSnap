package com.sirelon.sellsnap.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PlatformImeOptions

@Composable
@Suppress("UNUSED_PARAMETER")
internal actual fun rememberPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit,
): PlatformImeOptions? = null
