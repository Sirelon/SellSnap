package com.sirelon.sellsnap.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * SellSnap-aligned semantic colors exposed through `AppTheme.colors`.
 *
 * Public tokens:
 * `primary`, `primaryBright`, `onPrimary`, `background`, `onBackground`,
 * `surface`, `surfaceLowest`, `surfaceLow`, `surfaceHigh`, `surfaceVariant`,
 * `secondaryContainer`, `onSecondaryContainer`, `outline`, `outlineVariant`,
 * `success`, `warning`, `warningVariant`, `error`, `onError`,
 * `onSurface`, `onSurfaceMuted`, `onSurfaceSoft`.
 *
 * Material surface container roles are derived internally from these tokens.
 */
@Stable
data class AppColors(
    val primary: Color,
    val primaryBright: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surfaceHigh: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val success: Color,
    val warning: Color,
    val warningVariant: Color,
    val onSurfaceMuted: Color,
    val onSurfaceSoft: Color,
)

internal fun AppColors.toMaterial(darkTheme: Boolean): ColorScheme {
    val baseScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    val surfaceContainer = lerp(surfaceLow, surfaceHigh, 0.5f)
    val surfaceContainerHighest = lerp(surfaceHigh, secondaryContainer, 0.5f)
    return baseScheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryBright,
        onPrimaryContainer = onSecondaryContainer,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = success,
        onTertiary = onPrimary,
        tertiaryContainer = success.copy(alpha = 0.25f),
        onTertiaryContainer = success,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurface,
        surfaceContainerLowest = surfaceLowest,
        surfaceContainerLow = surfaceLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        error = error,
        onError = onError,
        errorContainer = error.copy(alpha = 0.2f),
        onErrorContainer = error,
        outline = outline,
        outlineVariant = outlineVariant,
        inverseSurface = Color(0xFF2A1400),
        inverseOnSurface = Color(0xFFFFEDD8),
    )
}
