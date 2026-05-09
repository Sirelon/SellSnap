package com.sirelon.sellsnap.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Concentric pulsing circles with a center content slot.
 * The outer circle pulses (scales) continuously.
 */
@Composable
fun PulsingCircles(
    modifier: Modifier = Modifier,
    outerColor: Color = AppTheme.colors.primary.copy(alpha = 0.2f),
    middleColor: Color = AppTheme.colors.primary.copy(alpha = 0.35f),
    innerColors: List<Color> = listOf(
        AppTheme.colors.primaryBright,
        AppTheme.colors.primary,
    ),
    content: @Composable () -> Unit = {},
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Box(
        modifier = modifier
            .size(AppDimens.Size.xl21)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .background(outerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(AppDimens.Size.xl18)
                .background(middleColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.Size.xl14)
                    .background(
                        brush = Brush.linearGradient(innerColors),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
