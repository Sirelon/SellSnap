package com.sirelon.sellsnap.features.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.Pill
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.dictate_pill
import com.sirelon.sellsnap.generated.resources.dictate_pill_listening
import com.sirelon.sellsnap.generated.resources.ic_mic
import org.jetbrains.compose.resources.stringResource

/** Starts and stops [speechToText]. While it listens, a [ListeningStrip] under the field shows what it hears. */
@Composable
fun DictatePill(
    speechToText: SpeechToText,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isListening = speechToText.isListening
    val color = when {
        !enabled -> AppTheme.colors.onSurfaceMuted
        isListening -> AppTheme.colors.error
        else -> AppTheme.colors.primary
    }
    Pill(
        color = color,
        text = stringResource(if (isListening) Res.string.dictate_pill_listening else Res.string.dictate_pill),
        iconResource = Res.drawable.ic_mic,
        onClick = if (enabled) {
            { if (isListening) speechToText.stop() else speechToText.start() }
        } else {
            null
        },
        modifier = modifier,
    )
}
