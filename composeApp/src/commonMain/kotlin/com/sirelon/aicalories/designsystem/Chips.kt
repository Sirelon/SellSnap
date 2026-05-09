package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

@Immutable
data class ChipData(
    val text: String,
    val icon: ImageVector?,
    val style: ChipStyle = ChipStyle.Neutral,
)

enum class ChipStyle {
    Success,
    Error,
    Neutral,
}

@Composable
fun TagGroup(
    title: String,
    tags: List<ChipData>,
    titleColor: Color = AppTheme.colors.onSurface,
) {
    if (tags.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
    ) {
        Text(
            text = title,
            style = AppTheme.typography.label,
            color = titleColor,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
        ) {
            tags.forEach { tag ->
                ChipComponent(
                    data = tag,
                )
            }
        }
    }
}

@ReadOnlyComposable
@Composable
private fun ChipStyle.toneColor(): Color {
    return when (this) {
        ChipStyle.Success -> AppTheme.colors.success
        ChipStyle.Error -> AppTheme.colors.error
        ChipStyle.Neutral -> AppTheme.colors.onSurface
    }
}


@ReadOnlyComposable
@Composable
private fun ChipStyle.containerColor(): Color {
    val tone = toneColor()
    return when (this) {
        ChipStyle.Success,
        ChipStyle.Error ->
            tone.copy(alpha = 0.12f) // light tinted background

        ChipStyle.Neutral ->
            AppTheme.colors.surfaceVariant // or onSurface.copy(alpha = 0.08f)
    }
}

@Composable
fun ChipComponent(
    data: ChipData,
    modifier: Modifier = Modifier,
) {
    val tone = data.style.toneColor()
    val container = data.style.containerColor()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.BorderRadius.m),
        color = container,
        contentColor = tone,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.Spacing.m,
                vertical = AppDimens.Spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            data.icon?.let {
                Icon(
                    modifier = Modifier
                        .height(IntrinsicSize.Max)
                        .padding(AppDimens.Spacing.xs),
                    imageVector = it,
                    contentDescription = null,
                    tint = tone,
                )
            }
            Text(
                text = data.text,
                style = AppTheme.typography.caption,
                color = tone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
