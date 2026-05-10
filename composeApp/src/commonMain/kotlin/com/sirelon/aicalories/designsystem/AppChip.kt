package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AppChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    enabled: Boolean = false,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    colors: AppChipColors = AppChipDefaults.neutralColors(),
) {
    val resolvedLeadingIcon = leadingIcon ?: icon?.let { image ->
        {
            Icon(
                imageVector = image,
                contentDescription = iconContentDescription,
            )
        }
    }

    AssistChip(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        // Pill shape — "Fluid Highlight" per design spec
        shape = RoundedCornerShape(percent = 50),
        label = {
            Text(
                text = text,
                style = AppTheme.typography.caption,
                color = colors.labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = AppDimens.Spacing.xs3),
            )
        },
        leadingIcon = resolvedLeadingIcon?.let { iconContent ->
            {
                CompositionLocalProvider(LocalContentColor provides colors.leadingIconColor) {
                    iconContent()
                }
            }
        },
        colors = colors.toChipColors(),
        border = null, // No-Line Rule: no border on chips
        elevation = AssistChipDefaults.assistChipElevation(elevation = AppDimens.Spacing.xs4),
    )
}

@Immutable
data class AppChipColors(
    val containerColor: Color,
    val labelColor: Color,
    val leadingIconColor: Color,
)

object AppChipDefaults {

    @Composable
    @ReadOnlyComposable
    fun neutralColors(): AppChipColors {
        val scheme = AppTheme.colors
        return AppChipColors(
            containerColor = scheme.secondaryContainer,
            labelColor = scheme.onSecondaryContainer,
            leadingIconColor = scheme.onSecondaryContainer,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun primaryColors(): AppChipColors {
        return accentColors(
            accent = AppTheme.colors.primary,
            onAccent = AppTheme.colors.onPrimary,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun successColors(): AppChipColors {
        return accentColors(
            accent = AppTheme.colors.success,
            onAccent = AppTheme.colors.success,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun errorColors(): AppChipColors {
        return accentColors(
            accent = AppTheme.colors.error,
            onAccent = AppTheme.colors.error,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun accentColors(
        accent: Color,
        onAccent: Color = accent,
    ): AppChipColors {
        val scheme = AppTheme.colors
        val containerColor = lerp(scheme.surfaceVariant, accent, 0.35f)
        return AppChipColors(
            containerColor = containerColor,
            labelColor = onAccent,
            leadingIconColor = onAccent,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun capacityColors(
        fitsPessimistic: Boolean,
        fitsOptimistic: Boolean,
    ): AppChipColors {
        return when {
            fitsPessimistic -> successColors()
            fitsOptimistic -> primaryColors()
            else -> errorColors()
        }
    }
}

@Composable
private fun AppChipColors.toChipColors(): ChipColors {
    val scheme = AppTheme.colors
    val disabledContainer = lerp(containerColor, scheme.surfaceVariant, 0.65f)
    val disabledLabel = lerp(labelColor, scheme.onSurfaceMuted, 0.65f)
    val disabledIcon = lerp(leadingIconColor, scheme.onSurfaceMuted, 0.65f)

    return AssistChipDefaults.assistChipColors(
        containerColor = containerColor,
        labelColor = labelColor,
        leadingIconContentColor = leadingIconColor,
        trailingIconContentColor = leadingIconColor,
        disabledContainerColor = disabledContainer,
        disabledLabelColor = disabledLabel,
        disabledLeadingIconContentColor = disabledIcon,
        disabledTrailingIconContentColor = disabledIcon,
    )
}
