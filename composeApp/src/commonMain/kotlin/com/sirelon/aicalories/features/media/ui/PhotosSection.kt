package com.sirelon.sellsnap.features.media.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import com.mohamedrejeb.calf.io.KmpFile
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.buttons.AppIconButton
import com.sirelon.sellsnap.features.media.upload.UploadingItem
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.add_photos_title
import com.sirelon.sellsnap.generated.resources.photos_count_format
import com.sirelon.sellsnap.generated.resources.take_photo
import org.jetbrains.compose.resources.stringResource

@Composable
fun PhotosSection(
    files: Map<KmpFile, UploadingItem>,
    onTakePhotoClick: () -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxPhotos: Int = 5
) {
    val photoCount = files.size

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.BorderRadius.xl7),
        color = AppTheme.colors.surface,
        shadowElevation = AppDimens.Spacing.xs2
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl6),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl5)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.add_photos_title),
                    fontSize = AppDimens.TextSize.xl5,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.onSurface
                )
                Text(
                    text = stringResource(Res.string.photos_count_format, photoCount, maxPhotos),
                    fontSize = AppDimens.TextSize.xl2,
                    color = AppTheme.colors.onSurfaceSoft,
                    fontWeight = FontWeight.Medium
                )
            }

            val canAddMore = photoCount < maxPhotos
            PhotosGridComponent(
                files = files,
                interactionEnabled = canAddMore,
                onAddPhoto = onTakePhotoClick,
                maxFiles = maxPhotos,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl)
            ) {
                AppButton(
                    modifier = Modifier.weight(1f),
                    style = AppButtonDefaults.secondary(),
                    text = stringResource(Res.string.take_photo),
                    onClick = onTakePhotoClick,
                    leadingIcon = rememberVectorPainter(Icons.Default.CameraAlt),
                )

                AppIconButton(
                    icon = rememberVectorPainter(Icons.Default.FileUpload),
                    onClick = onUploadClick,
                )
            }
        }
    }
}