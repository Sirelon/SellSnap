package com.sirelon.sellsnap.features.media.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.source_camera
import com.sirelon.sellsnap.generated.resources.source_gallery
import org.jetbrains.compose.resources.stringResource

private val PILL_HEIGHT = 52.dp

@Composable
fun CameraGalleryPicker(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l),
    ) {
        SourcePill(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.CameraAlt,
            label = stringResource(Res.string.source_camera),
            enabled = enabled,
            onClick = onCameraClick,
        )
        SourcePill(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Image,
            label = stringResource(Res.string.source_gallery),
            enabled = enabled,
            onClick = onGalleryClick,
        )
    }
}

@Composable
private fun SourcePill(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(PILL_HEIGHT),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(AppDimens.BorderRadius.xl2),
        color = AppTheme.colors.surfaceLowest,
        border = BorderStroke(
            width = AppDimens.BorderWidth.s,
            color = AppTheme.colors.outlineVariant.copy(alpha = 0.27f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.Spacing.xl3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                AppDimens.Spacing.m,
                Alignment.CenterHorizontally,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.colors.primary,
            )
            Text(
                text = label,
                color = AppTheme.colors.onSurface,
                fontSize = AppDimens.TextSize.xl2,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CameraGalleryPickerPreview() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.colors.background,
        ) {
            CameraGalleryPicker(
                onCameraClick = {},
                onGalleryClick = {},
                modifier = Modifier.padding(AppDimens.Spacing.xl5),
            )
        }
    }
}
