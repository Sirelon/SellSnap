package com.sirelon.sellsnap.features.media.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.UploadStatusIndicator
import com.sirelon.sellsnap.features.media.upload.UploadingItem

@Composable
fun PhotosGridComponent(
    files: Map<KmpFile, UploadingItem>,
    interactionEnabled: Boolean,
    onAddPhoto: () -> Unit,
    gridSize: Int = 3,
    maxFiles: Int = 5,
) {
    val spacing = AppDimens.Spacing.xl3
    val arrangement = Arrangement.spacedBy(spacing)
    BoxWithConstraints {
        val itemWidth = maxWidth / gridSize - spacing
        val itemModifier = Modifier
            .size(itemWidth)
            .aspectRatio(1f)

        FlowRow(
            horizontalArrangement = arrangement,
            verticalArrangement = arrangement,
        ) {
            files.forEach { (file, upload) ->
                PhotoContainer(
                    modifier = itemModifier,
                    onClick = {
                        // TODO: remove photo
                    },
                    enabled = interactionEnabled,
                    isDashed = false,
                    content = {
                        AppAsyncImage(
                            modifier = Modifier,
                            model = file,
                        )

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
                    }
                )
            }

            // Only show empty cards in the first row
            val emptyItemsInFirstRow = maxOf(0, gridSize - files.size % gridSize)
            if (files.size < gridSize) {
                repeat(emptyItemsInFirstRow) {
                    PhotoContainer(
                        modifier = itemModifier,
                        onClick = onAddPhoto,
                        enabled = interactionEnabled,
                        isDashed = true,
                        content = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                tint = AppTheme.colors.onSurface.copy(alpha = 0.6f),
                                contentDescription = stringResource(Res.string.add_photo_cd),
                                modifier = Modifier.size(AppDimens.Size.xl8),
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    shape: Shape,
) = drawBehind {
    val stroke = Stroke(
        width = width.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    )
    drawPath(
        path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(
                        offset = Offset(0f, 0f),
                        size = Size(size.width, size.height)
                    ),
                    radiusX = 12f,
                    radiusY = 12f
                )
            )
        },
        color = color,
        style = stroke
    )
}

@Composable
private fun PhotoContainer(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    isDashed: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = if (isDashed) {
            modifier.dashedBorder(
                width = AppDimens.BorderWidth.l,
                color = AppTheme.colors.outline.copy(alpha = if (enabled) 1f else 0.4f),
                shape = RoundedCornerShape(AppDimens.BorderRadius.xl3),
            )
        } else {
            modifier
        },
        shape = RoundedCornerShape(AppDimens.BorderRadius.xl3),
        border = if (!isDashed) BorderStroke(
            width = AppDimens.BorderWidth.l,
            color = AppTheme.colors.outline.copy(alpha = if (enabled) 1f else 0.4f),
        ) else null,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
