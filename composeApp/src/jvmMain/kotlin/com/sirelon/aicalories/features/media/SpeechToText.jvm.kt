package com.sirelon.sellsnap.features.media

import androidx.compose.runtime.Composable

@Composable
actual fun rememberSpeechToText(
    languageTag: String?,
    onResult: (String) -> Unit,
    onError: (SpeechToTextError) -> Unit,
): SpeechToText? = null
