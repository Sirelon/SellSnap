package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(AppDimens.BorderRadius.xl3),
    containerColor: Color = AppTheme.colors.surfaceLowest,
    contentColor: Color = AppTheme.colors.onSurface,
    shadowElevation: Dp = 2.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val ambientShadowModifier = modifier.shadow(
        elevation = shadowElevation,
        shape = shape,
        spotColor = AppTheme.colors.onSurface.copy(alpha = 0.06f),
        ambientColor = AppTheme.colors.onSurface.copy(alpha = 0.04f),
    )

    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = contentColor,
    )
    // Zero tonal elevation — depth is achieved via ambient shadow and tonal layering
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = ambientShadowModifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            interactionSource = interactionSource,
            content = content,
        )
    } else {
        Card(
            modifier = ambientShadowModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = content,
        )
    }
}
