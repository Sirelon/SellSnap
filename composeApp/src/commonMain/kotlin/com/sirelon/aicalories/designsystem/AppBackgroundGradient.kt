package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun AppBackgroundGradient(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    val isDark = colors.background.luminance() < 0.5f
    val primaryGlowAlpha = if (isDark) 0.24f else 0.14f
    val warmGlowAlpha = if (isDark) 0.18f else 0.20f

    Box(
        modifier = modifier
            .background(colors.background)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.background,
                        colors.primary.copy(alpha = primaryGlowAlpha),
                        colors.surfaceHigh.copy(alpha = warmGlowAlpha),
                        colors.background,
                    ),
                ),
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.warningVariant.copy(alpha = warmGlowAlpha),
                        Color.Transparent,
                    ),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY,
                ),
            ),
    ) {
        content()
    }
}
