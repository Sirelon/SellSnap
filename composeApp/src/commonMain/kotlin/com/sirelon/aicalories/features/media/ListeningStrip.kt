package com.sirelon.sellsnap.features.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.dictate_pill_listening
import org.jetbrains.compose.resources.stringResource

private const val BarCount = 24
private const val SampleMillis = 90
private const val MinBarFraction = 0.1f
private const val PartialTextMaxLines = 2

/**
 * Shown under the field [speechToText] dictates into, only while it listens: a waveform of the
 * recent microphone level with a stop button, and under it the words heard so far.
 */
@Composable
fun ListeningStrip(
    speechToText: SpeechToText,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = speechToText.isListening,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.error.copy(alpha = 0.08f), RoundedCornerShape(AppDimens.BorderRadius.xl3))
                .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.m)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl),
            ) {
                LevelWaveform(
                    level = { speechToText.level },
                    color = AppTheme.colors.error,
                    modifier = Modifier
                        .weight(1f)
                        .height(AppDimens.Size.xl9),
                )
                StopButton(onClick = speechToText::stop)
            }
            val partialText = speechToText.partialText
            if (partialText.isNotEmpty()) {
                LatestLines(text = partialText)
            }
        }
    }
}

/**
 * One bar per [SampleMillis] of microphone level, newest on the right. The row slides left
 * continuously between samples, so it reads as a moving recording rather than ticking bars.
 */
@Composable
private fun LevelWaveform(
    level: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val currentLevel by rememberUpdatedState(level)
    // Oldest first. One more than fits, for the sample sliding in at the right edge.
    val samples = remember { mutableStateListOf<Float>().apply { repeat(BarCount + 1) { add(0f) } } }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            samples.removeAt(0)
            samples.add(currentLevel().coerceIn(0f, 1f))
            slide.snapTo(0f)
            slide.animateTo(1f, tween(SampleMillis, easing = LinearEasing))
        }
    }
    Canvas(modifier = modifier.clipToBounds()) {
        val step = size.width / BarCount
        val barWidth = step * 0.5f
        val progress = slide.value
        samples.forEachIndexed { index, sample ->
            val left = (index - progress) * step + (step - barWidth) / 2f
            val barHeight = size.height * (MinBarFraction + (1f - MinBarFraction) * sample)
            // Older bars fade towards the left edge.
            val alpha = 0.3f + 0.7f * ((left + barWidth / 2f) / size.width).coerceIn(0f, 1f)
            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(x = left, y = (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    val description = stringResource(Res.string.dictate_pill_listening)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = AppTheme.colors.error,
        modifier = Modifier
            .size(AppDimens.Size.xl10)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(AppDimens.Size.xl)
                    .background(AppTheme.colors.onError, RoundedCornerShape(AppDimens.BorderRadius.xs2)),
            )
        }
    }
}

/** At most [PartialTextMaxLines] lines of [text], its last ones: the words just heard. */
@Composable
private fun LatestLines(text: String) {
    val style = AppTheme.typography.body
    val maxHeight = with(LocalDensity.current) { (style.lineHeight * PartialTextMaxLines).toDp() }
    // Laid out whole and pinned to the bottom, so the lines that overflow are the oldest ones.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clipToBounds(),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = text,
            style = style,
            color = AppTheme.colors.onSurfaceMuted,
            modifier = Modifier.wrapContentHeight(align = Alignment.Bottom, unbounded = true),
        )
    }
}
