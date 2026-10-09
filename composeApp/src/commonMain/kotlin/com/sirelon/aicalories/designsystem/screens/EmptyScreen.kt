package com.sirelon.sellsnap.designsystem.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_sparkles
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Full-screen empty state. Uses the same icon tile, colors and button as the My Ads state cards
 * (`MyAdvertsScreen.StateCard`) so empty and error states look alike across the app.
 *
 * The action button is shown only when both [actionLabel] and [onActionClick] are set.
 */
@Composable
fun EmptyScreen(
    title: String,
    description: String,
    actionLabel: String?,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .background(AppTheme.colors.background)
            .padding(AppDimens.Spacing.xl8),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = AppDimens.Size.xl25),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(AppDimens.Size.xl12)
                        .clip(RoundedCornerShape(AppDimens.BorderRadius.xl2))
                        .background(AppTheme.colors.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = AppTheme.colors.primary,
                    )
                }
            }
            Text(
                text = title,
                style = AppTheme.typography.title,
                color = AppTheme.colors.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onActionClick != null) {
                AppButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    style = AppButtonDefaults.primary(),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun EmptyScreenPreview() {
    AppTheme {
        EmptyScreen(
            title = "Nothing here yet",
            description = "Check back later for release notes.",
            actionLabel = "Refresh",
            icon = Res.drawable.ic_sparkles,
            onActionClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
