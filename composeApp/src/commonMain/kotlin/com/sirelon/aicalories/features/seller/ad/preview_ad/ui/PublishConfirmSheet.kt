package com.sirelon.sellsnap.features.seller.ad.preview_ad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.publish_confirm_back
import com.sirelon.sellsnap.generated.resources.publish_confirm_subtitle
import com.sirelon.sellsnap.generated.resources.publish_confirm_title
import com.sirelon.sellsnap.generated.resources.publish_confirm_yes
import org.jetbrains.compose.resources.stringResource

@Composable
fun PublishConfirmSheet(
    imageUrl: String?,
    title: String,
    categoryLabel: String,
    priceFormatted: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl4)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        if (imageUrl != null) {
            AppAsyncImage(
                model = imageUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimens.Size.xl25)
                    .clip(RoundedCornerShape(AppDimens.BorderRadius.l)),
            )
        }

        Text(
            text = stringResource(Res.string.publish_confirm_title),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )

        Text(
            text = stringResource(Res.string.publish_confirm_subtitle),
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )

        AdSummaryRow(label = title)
        AdSummaryRow(label = categoryLabel)
        AdSummaryRow(label = priceFormatted, isBold = true)

        Spacer(modifier = Modifier.height(AppDimens.Spacing.m))

        AppButton(
            modifier = Modifier.fillMaxWidth(),
            style = AppButtonDefaults.success(),
            text = stringResource(Res.string.publish_confirm_yes),
            onClick = onConfirm,
        )
        AppButton(
            modifier = Modifier.fillMaxWidth(),
            style = AppButtonDefaults.secondary(),
            text = stringResource(Res.string.publish_confirm_back),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun AdSummaryRow(label: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.body,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = AppTheme.colors.onBackground,
        )
    }
}

@PreviewLightDark
@Composable
private fun PublishConfirmSheetPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PublishConfirmSheet(
                    imageUrl = null,
                    title = "Nike Air Max 90, size 42, worn 2 months",
                    categoryLabel = "Shoes / Sneakers",
                    priceFormatted = "₴ 1,800",
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
    }
}
