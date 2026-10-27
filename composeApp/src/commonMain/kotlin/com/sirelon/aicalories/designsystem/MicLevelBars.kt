package com.sirelon.sellsnap.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

private const val BarCount = 4
private const val MinBarFraction = 0.25f
private const val WavePeriodMillis = 700

/**
 * A few vertical bars that rise and fall with the microphone [level] (0..1), as a stand-in for
 * the mic icon while listening. The bars sit [AppDimens.Size.xl] square, the icon's size. Each
 * bar follows its own phase of a slow wave, so loud speech reads as a moving wave and silence as
 * a row of short bars.
 */
@Composable
fun MicLevelBars(
    level: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val smoothedLevel by animateFloatAsState(
        targetValue = level().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "micLevel",
    )
    val phase by rememberInfiniteTransition(label = "micWave").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(WavePeriodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "micWavePhase",
    )
    Canvas(modifier = modifier.size(AppDimens.Size.xl)) {
        val barWidth = size.width / (BarCount * 2 - 1)
        for (index in 0 until BarCount) {
            // Each bar's wave sits between 0.5 and 1 of the level, offset from its neighbour.
            val wave = 0.75f + 0.25f * sin(phase + index * 1.6f)
            val fraction = (MinBarFraction + (1f - MinBarFraction) * smoothedLevel * wave).coerceAtMost(1f)
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(x = index * 2 * barWidth, y = (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
