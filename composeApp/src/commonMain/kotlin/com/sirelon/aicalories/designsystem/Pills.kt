package com.sirelon.sellsnap.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.error_pill_default
import com.sirelon.sellsnap.generated.resources.ic_circle_alert
import com.sirelon.sellsnap.generated.resources.ic_thumbs_down
import com.sirelon.sellsnap.generated.resources.ic_thumbs_up
import com.sirelon.sellsnap.generated.resources.vote_down_label
import com.sirelon.sellsnap.generated.resources.vote_up_label
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ErrorPill(
    label: String = stringResource(Res.string.error_pill_default),
    modifier: Modifier = Modifier,
) {
    Pill(
        color = AppTheme.colors.error,
        text = label,
        iconResource = Res.drawable.ic_circle_alert,
        modifier = modifier
    )
}

@Composable
fun Pill(
    text: String,
    iconResource: DrawableResource,
    color: Color,
    modifier: Modifier = Modifier,
    bgColor: Color = color.copy(alpha = 0.18f),
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bgColor,
        onClick = onClick ?: {},
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = AppDimens.Spacing.m,
                vertical = AppDimens.Spacing.xs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                AppDimens.Spacing.xs,
                Alignment.CenterHorizontally
            )
        ) {
            Icon(
                painter = painterResource(iconResource),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(AppDimens.Size.xl),
            )
            Text(
                text = text,
                style = AppTheme.typography.caption.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = color,
            )
        }
    }
}

@Composable
fun LoadingPill(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.primary,
    bgColor: Color = AppTheme.colors.surfaceLow,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bgColor,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(AppDimens.Spacing.s)
                .size(AppDimens.Size.xl),
            strokeWidth = 2.dp,
            color = color,
        )
    }
}

@Composable
fun ThumbsVoteRow(
    isUpSelected: Boolean,
    isDownSelected: Boolean,
    onUpVote: () -> Unit,
    onDownVote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
    ) {
        Pill(
            color = if (isUpSelected) AppTheme.colors.success else AppTheme.colors.onSurfaceMuted,
            text = stringResource(Res.string.vote_up_label),
            iconResource = Res.drawable.ic_thumbs_up,
            onClick = onUpVote,
        )
        Pill(
            color = if (isDownSelected) AppTheme.colors.error else AppTheme.colors.onSurfaceMuted,
            text = stringResource(Res.string.vote_down_label),
            iconResource = Res.drawable.ic_thumbs_down,
            onClick = onDownVote,
        )
    }
}

@PreviewLightDark
@Composable
private fun ErrorPillPreview() {
    AppTheme {
        ErrorPill()
    }
}

@PreviewLightDark
@Composable
private fun LoadingPillPreview() {
    AppTheme {
        LoadingPill()
    }
}

@PreviewLightDark
@Composable
private fun ThumbsVoteRowPreview() {
    AppTheme {
        ThumbsVoteRow(
            isUpSelected = true,
            isDownSelected = false,
            onUpVote = {},
            onDownVote = {},
        )
    }
}