package com.sirelon.sellsnap.designsystem.buttons

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.performStepFeedback

/**
 * Shared shape + height tokens for every [AppButton] variant — matches the
 * SellSnap design prototype (60dp tall, 18dp corner radius).
 */
private val ButtonShape = RoundedCornerShape(AppDimens.BorderRadius.xl4)
private val ButtonHeight = AppDimens.Size.xl8 + AppDimens.Size.xl7 // 60.dp

/**
 * Press animation timings taken from `Design/ClaudeDesign/project/ui.jsx`:
 * transform 120ms ease, shadow/background 180ms ease.
 */
private const val PressTransformDurationMs = 120
private const val PressShadowDurationMs = 180

/**
 * The primary text/icon button in the design system.
 *
 * Supports solid, gradient, and inset-highlight variants through
 * [AppButtonStyle]. On press the button animates to a subtle
 * `translateY(1dp) scale(0.995f)` pose and flattens its shadow —
 * matching `AppButton` in the SellSnap prototype.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonDefaults.primary(),
    leadingIcon: Painter? = null,
    trailingIcon: Painter? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.995f else 1f,
        animationSpec = tween(PressTransformDurationMs),
        label = "AppButton.scale",
    )
    val pressTranslateY by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 0.dp,
        animationSpec = tween(PressTransformDurationMs),
        label = "AppButton.translateY",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed || !enabled) 0.dp else style.elevation,
        animationSpec = tween(PressShadowDurationMs),
        label = "AppButton.elevation",
    )

    val gradient = style.gradient
    val useShadow = style.shadowColor.isSpecified() && enabled

    Box(
        modifier = modifier
            .height(ButtonHeight)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationY = pressTranslateY.toPx()
            }
            .then(
                if (useShadow) {
                    Modifier.shadow(
                        elevation = animatedElevation,
                        shape = ButtonShape,
                        ambientColor = style.shadowColor,
                        spotColor = style.shadowColor,
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        Button(
            onClick = {
                hapticFeedback.performStepFeedback()
                onClick()
            },
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (gradient != null && enabled) {
                        Modifier.background(gradient, ButtonShape)
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (style.innerHighlight && enabled && !isPressed) {
                        Modifier.drawBehind { drawInsetTopHighlight() }
                    } else {
                        Modifier
                    },
                ),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (gradient != null) Color.Transparent else style.backgroundColor,
                contentColor = style.contentColor,
                disabledContainerColor = if (gradient != null) {
                    Color.Transparent
                } else {
                    style.backgroundColor.copy(alpha = 0.5f)
                },
                disabledContentColor = style.contentColor.copy(alpha = 0.5f),
            ),
            // External `Modifier.shadow` owns the elevation; disable Button's own shadow to avoid doubling.
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
            ) {
                leadingIcon?.let {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.Size.xl5),
                    )
                }
                Text(
                    text = text,
                    fontSize = AppDimens.TextSize.xl4,
                    fontWeight = FontWeight.Bold,
                )
                trailingIcon?.let {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        modifier = Modifier.size(AppDimens.Size.xl5),
                    )
                }
            }
        }
    }
}

/**
 * Mimics the `inset 0 1px 0 rgba(255,255,255,0.25)` highlight from the
 * `magic` variant in the design prototype. We draw a 1dp tall white band at
 * the top of the button, inside the rounded shape, to simulate the "glass"
 * rim.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInsetTopHighlight() {
    val bandHeight = 1.dp.toPx()
    val topInset = 0f
    val horizontalInset = size.width * 0.04f // keep highlight inside the corner arc
    drawRoundRect(
        color = Color.White.copy(alpha = 0.25f),
        topLeft = Offset(x = horizontalInset, y = topInset),
        size = Size(width = size.width - horizontalInset * 2, height = bandHeight),
    )
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

/**
 * Visual style for [AppButton]. Keep the same instance across recompositions
 * by constructing it inside the [AppButtonDefaults] factories — they are
 * `@ReadOnlyComposable` so they resolve to static objects per theme.
 *
 * @param backgroundColor solid fill used when [gradient] is null.
 * @param contentColor text + icon color.
 * @param elevation shadow elevation in the default (un-pressed) state; drops
 *                  to 0 on press and when disabled.
 * @param gradient optional fill brush used instead of [backgroundColor].
 * @param shadowColor if specified, the shadow tint — matches the colored drop
 *                    shadows in the design spec. Fall back is [Color.Unspecified]
 *                    which disables the custom shadow entirely.
 * @param innerHighlight draws a thin white band at the top inside the shape
 *                       to emulate the CSS `inset 0 1px 0 rgba(...)` used by
 *                       the `magic` variant.
 */
@Immutable
data class AppButtonStyle(
    val backgroundColor: Color,
    val contentColor: Color,
    val elevation: Dp = AppDimens.Size.xs,
    val gradient: Brush? = null,
    val shadowColor: Color = Color.Unspecified,
    val innerHighlight: Boolean = false,
)

data object AppButtonDefaults {

    /**
     * Primary CTA — warm-orange gradient with a soft colored drop shadow.
     * Matches `styles.primary` in `ui.jsx`.
     */
    @Composable
    @ReadOnlyComposable
    fun primary(): AppButtonStyle {
        val primary = AppTheme.colors.primary
        val primaryBright = AppTheme.colors.primaryBright
        return AppButtonStyle(
            backgroundColor = primary,
            contentColor = AppTheme.colors.onPrimary,
            elevation = AppDimens.Size.m, // 8dp — roughly `0 8px 24px -8px`
            gradient = Brush.linearGradient(
                colors = listOf(primary, primaryBright),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            ),
            shadowColor = primary.copy(alpha = 0.55f),
        )
    }

    /**
     * Neutral filled button — surface-toned, with a subtle shadow.
     * Matches `styles.secondary` in `ui.jsx`.
     */
    @Composable
    @ReadOnlyComposable
    fun secondary(): AppButtonStyle {
        return AppButtonStyle(
            backgroundColor = AppTheme.colors.surfaceHigh,
            contentColor = AppTheme.colors.onSurface,
            elevation = AppDimens.Spacing.xs2, // 2dp — very subtle
            shadowColor = AppTheme.colors.onSurface.copy(alpha = 0.10f),
        )
    }

    /**
     * Outline-ish filled button used for auth flows (kept for compatibility —
     * mapped to the `secondary` visual with a richer text color).
     */
    @Composable
    @ReadOnlyComposable
    fun outline(): AppButtonStyle {
        return AppButtonStyle(
            backgroundColor = AppTheme.colors.surfaceHigh,
            contentColor = AppTheme.colors.onBackground,
            elevation = AppDimens.Spacing.xs4,
        )
    }

    /**
     * Transparent button with primary-tinted text — the `ghost` variant in
     * the design prototype.
     */
    @Composable
    @ReadOnlyComposable
    fun ghost(): AppButtonStyle {
        return AppButtonStyle(
            backgroundColor = Color.Transparent,
            contentColor = AppTheme.colors.primary,
            elevation = AppDimens.Spacing.xs4,
        )
    }

    /**
     * "Magic" AI button — triple-stop warm gradient with an inner top
     * highlight and a pronounced colored glow. Matches `styles.magic` in
     * `ui.jsx`. Use it for the primary AI CTA (e.g. "Generate Ad with AI").
     */
    @Composable
    @ReadOnlyComposable
    fun magic(): AppButtonStyle {
        val primary = AppTheme.colors.primary
        val primaryBright = AppTheme.colors.primaryBright
        val warningVariant = AppTheme.colors.warningVariant
        return AppButtonStyle(
            backgroundColor = primary,
            contentColor = AppTheme.colors.onPrimary,
            elevation = AppDimens.Size.l, // 10dp — roughly `0 10px 28px -6px`
            gradient = Brush.linearGradient(
                colors = listOf(primary, primaryBright, warningVariant),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            ),
            shadowColor = primary.copy(alpha = 0.56f),
            innerHighlight = true,
        )
    }

    /**
     * Success action — solid green with a matching colored glow. Used for
     * confirm/publish states. Matches `styles.success` in `ui.jsx`.
     */
    @Composable
    @ReadOnlyComposable
    fun success(): AppButtonStyle {
        val success = AppTheme.colors.success
        return AppButtonStyle(
            backgroundColor = success,
            contentColor = Color.White,
            elevation = AppDimens.Size.m, // 8dp — roughly `0 8px 20px -6px`
            shadowColor = success.copy(alpha = 0.40f),
        )
    }
}
