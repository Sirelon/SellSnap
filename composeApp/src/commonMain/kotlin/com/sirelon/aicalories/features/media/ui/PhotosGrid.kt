package com.sirelon.sellsnap.features.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.UploadStatusIndicator
import com.sirelon.sellsnap.features.media.upload.UploadingItem
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.add_photo_cd
import com.sirelon.sellsnap.generated.resources.add_photo_label
import com.sirelon.sellsnap.generated.resources.remove_photo_cd
import org.jetbrains.compose.resources.stringResource

const val MAX_PHOTOS: Int = 5
private const val GRID_COLUMNS: Int = 3

@Composable
fun PhotosGrid(
    files: Map<KmpFile, UploadingItem>,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (KmpFile) -> Unit,
    modifier: Modifier = Modifier,
    maxPhotos: Int = MAX_PHOTOS,
    interactionEnabled: Boolean = true,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val itemWidth = (maxWidth - AppDimens.Spacing.l * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val cellModifier = Modifier.size(itemWidth)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l),
        ) {
            files.forEach { (file, upload) ->
                PhotoThumbnailCell(
                    modifier = cellModifier,
                    onRemove = { onRemovePhoto(file) },
                    interactionEnabled = interactionEnabled,
                    upload = upload,
                ) {
                    AppAsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = file,
                    )
                }
            }
            if (files.size < maxPhotos) {
                AddPhotoTile(
                    modifier = cellModifier,
                    enabled = interactionEnabled,
                    onClick = onAddPhoto,
                )
            }
        }
    }
}

@Composable
private fun PhotoThumbnailCell(
    modifier: Modifier,
    onRemove: () -> Unit,
    interactionEnabled: Boolean,
    upload: UploadingItem,
    image: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.clip(RoundedCornerShape(AppDimens.BorderRadius.xl2))) {
        image()

        if (upload.isUploading) {
            UploadStatusIndicator(progress = upload.progress)
        }

        if (upload.hasFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.error.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = AppTheme.colors.onError,
                    modifier = Modifier.size(AppDimens.Size.xl8),
                )
            }
        }

        RemovePhotoButton(
            modifier = Modifier.align(Alignment.TopEnd),
            enabled = interactionEnabled,
            onClick = onRemove,
        )
    }
}

@Composable
private fun RemovePhotoButton(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.padding(AppDimens.Spacing.s),
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Box(
            modifier = Modifier.size(AppDimens.Size.xl6),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.remove_photo_cd),
                tint = Color.White,
                modifier = Modifier.size(AppDimens.Size.xl2),
            )
        }
    }
}

@Composable
private fun AddPhotoTile(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.dashedBorder(
            width = AppDimens.BorderWidth.l,
            color = AppTheme.colors.outline.copy(alpha = 0.4f),
            cornerRadius = AppDimens.BorderRadius.xl2,
        ),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(AppDimens.BorderRadius.xl2),
        color = AppTheme.colors.surfaceLow,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(Res.string.add_photo_cd),
                tint = AppTheme.colors.primary,
                modifier = Modifier.size(AppDimens.Size.xl6),
            )
            Spacer(Modifier.size(AppDimens.Spacing.s))
            Text(
                text = stringResource(Res.string.add_photo_label),
                color = AppTheme.colors.primary,
                fontSize = AppDimens.TextSize.xl,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
): Modifier = drawBehind {
    val strokePx = width.toPx()
    val radiusPx = cornerRadius.toPx()
    val dashPx = 10.dp.toPx()
    val stroke = Stroke(
        width = strokePx,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx)),
    )
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = strokePx / 2f,
                top = strokePx / 2f,
                right = size.width - strokePx / 2f,
                bottom = size.height - strokePx / 2f,
                radiusX = radiusPx,
                radiusY = radiusPx,
            )
        )
    }
    drawPath(path = path, color = color, style = stroke)
}

// region Previews

@PreviewLightDark
@Composable
private fun PhotosGridEmptyPreview() {
    AppTheme {
        PreviewSurface { PhotosGridPreviewBody(photoCount = 0) }
    }
}

@PreviewLightDark
@Composable
private fun PhotosGridSinglePreview() {
    AppTheme {
        PreviewSurface { PhotosGridPreviewBody(photoCount = 1) }
    }
}

@PreviewLightDark
@Composable
private fun PhotosGridFourPreview() {
    AppTheme {
        PreviewSurface { PhotosGridPreviewBody(photoCount = 4) }
    }
}

@PreviewLightDark
@Composable
private fun PhotosGridFullPreview() {
    AppTheme {
        PreviewSurface { PhotosGridPreviewBody(photoCount = MAX_PHOTOS) }
    }
}

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppTheme.colors.background,
    ) {
        Box(modifier = Modifier.padding(AppDimens.Spacing.xl5)) {
            content()
        }
    }
}

@Composable
private fun PhotosGridPreviewBody(photoCount: Int) {
    val swatches = listOf(
        Color(0xFFB67A48),
        Color(0xFF8C5A3C),
        Color(0xFFD9A070),
        Color(0xFF7A5030),
        Color(0xFFE0B894),
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemWidth = (maxWidth - AppDimens.Spacing.l * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val cellModifier = Modifier.size(itemWidth).aspectRatio(1f)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l),
        ) {
            repeat(photoCount) { index ->
                PhotoThumbnailCell(
                    modifier = cellModifier,
                    onRemove = {},
                    interactionEnabled = true,
                    upload = UploadingItem(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(swatches[index % swatches.size])
                    )
                }
            }
            if (photoCount < MAX_PHOTOS) {
                AddPhotoTile(modifier = cellModifier, enabled = true, onClick = {})
            }
        }
    }
}

// endregion
