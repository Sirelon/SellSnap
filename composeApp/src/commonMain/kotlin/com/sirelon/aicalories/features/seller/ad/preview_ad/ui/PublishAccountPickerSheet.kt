package com.sirelon.sellsnap.features.seller.ad.preview_ad.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppAvatar
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.Pill
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PublishAccountPickerItem
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.account_business_badge
import com.sirelon.sellsnap.generated.resources.account_needs_reconnect_badge
import com.sirelon.sellsnap.generated.resources.ic_check
import com.sirelon.sellsnap.generated.resources.ic_circle_alert
import com.sirelon.sellsnap.generated.resources.ic_user
import com.sirelon.sellsnap.generated.resources.publish_target_account_picker_subtitle
import com.sirelon.sellsnap.generated.resources.publish_target_account_picker_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Account picker opened from the preview screen's "Publish to" row (SIR-83 U6). Switching here
 * calls [com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository.setActiveAccount]
 * with `fromPublishScreen = true`; the draft itself is never touched (D2). `NeedsReconnect` rows
 * are shown (so the account doesn't appear to have vanished) but are not selectable here -
 * reconnecting is an OAuth flow that belongs to Profile, not this screen.
 */
@Composable
fun PublishAccountPickerSheet(
    items: List<PublishAccountPickerItem>,
    onAccountSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preview_ad_account_picker_sheet")
            .padding(horizontal = AppDimens.Spacing.xl4)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
    ) {
        Text(
            text = stringResource(Res.string.publish_target_account_picker_title),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )
        Text(
            text = stringResource(Res.string.publish_target_account_picker_subtitle),
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )

        items.forEach { item ->
            key(item.localIndex) {
                AccountPickerRow(
                    item = item,
                    onClick = {
                        if (!item.needsReconnect && !item.isActive) {
                            onAccountSelected(item.localIndex)
                        } else if (item.isActive) {
                            onDismiss()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountPickerRow(
    item: PublishAccountPickerItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preview_ad_account_picker_row_${item.localIndex}")
            .clickable(enabled = !item.needsReconnect, onClick = onClick)
            .padding(vertical = AppDimens.Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
    ) {
        AppAvatar(
            avatarUrl = item.avatarUrl,
            fallbackInitial = item.name.trim().firstOrNull()?.uppercase() ?: "?",
            size = AppDimens.Size.xl8,
            useGradientBackground = false,
            initialStyle = AppTheme.typography.body,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
            ) {
                Text(
                    text = item.name,
                    style = AppTheme.typography.body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isBusiness) {
                    Pill(
                        text = stringResource(Res.string.account_business_badge),
                        iconResource = Res.drawable.ic_user,
                        color = AppTheme.colors.onSecondaryContainer,
                    )
                }
            }
            if (item.email.isNotBlank()) {
                Text(
                    text = item.email,
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            item.needsReconnect -> Pill(
                text = stringResource(Res.string.account_needs_reconnect_badge),
                iconResource = Res.drawable.ic_circle_alert,
                color = AppTheme.colors.warning,
            )

            item.isActive -> Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = AppTheme.colors.success,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PublishAccountPickerSheetPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            PublishAccountPickerSheet(
                items = listOf(
                    PublishAccountPickerItem(
                        localIndex = 1,
                        name = "Olena Kovalenko",
                        email = "olena@example.com",
                        avatarUrl = null,
                        isBusiness = false,
                        isActive = true,
                        needsReconnect = false,
                    ),
                    PublishAccountPickerItem(
                        localIndex = 2,
                        name = "Kovalenko Shop",
                        email = "shop@example.com",
                        avatarUrl = null,
                        isBusiness = true,
                        isActive = false,
                        needsReconnect = false,
                    ),
                    PublishAccountPickerItem(
                        localIndex = 3,
                        name = "Old Account",
                        email = "old@example.com",
                        avatarUrl = null,
                        isBusiness = false,
                        isActive = false,
                        needsReconnect = true,
                    ),
                ),
                onAccountSelected = {},
                onDismiss = {},
            )
        }
    }
}
