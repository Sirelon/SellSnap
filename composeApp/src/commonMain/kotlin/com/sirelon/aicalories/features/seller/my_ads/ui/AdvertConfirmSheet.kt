package com.sirelon.sellsnap.features.seller.my_ads.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.ActionConfirm
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.advert_confirm_cancel
import com.sirelon.sellsnap.generated.resources.advert_confirm_delete_action
import com.sirelon.sellsnap.generated.resources.advert_confirm_delete_message
import com.sirelon.sellsnap.generated.resources.advert_confirm_delete_message_active
import com.sirelon.sellsnap.generated.resources.advert_confirm_delete_title
import com.sirelon.sellsnap.generated.resources.advert_confirm_extend_action
import com.sirelon.sellsnap.generated.resources.advert_confirm_extend_message
import com.sirelon.sellsnap.generated.resources.advert_confirm_extend_title
import com.sirelon.sellsnap.generated.resources.advert_confirm_finish_action
import com.sirelon.sellsnap.generated.resources.advert_confirm_finish_message
import com.sirelon.sellsnap.generated.resources.advert_confirm_finish_title
import com.sirelon.sellsnap.generated.resources.advert_confirm_reactivate_action
import com.sirelon.sellsnap.generated.resources.advert_confirm_reactivate_message
import com.sirelon.sellsnap.generated.resources.advert_confirm_reactivate_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmation for a state-changing action (SIR-101).
 *
 * Every message states what happens on OLX rather than what happens in the app, and destructive
 * wording is reserved for the one action that genuinely destroys something. Deleting a listing
 * that is still live says so explicitly, because OLX has to take it down first - the seller ends
 * up answering "did it sell?" on the way through, and being told that up front beats discovering
 * it mid-flow.
 */
@Composable
fun AdvertConfirmSheet(
    confirm: ActionConfirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advert_confirm_sheet")
            .padding(horizontal = AppDimens.Spacing.xl4)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        Text(
            text = stringResource(confirmTitle(confirm.action)),
            style = AppTheme.typography.headline,
            color = AppTheme.colors.onBackground,
        )
        Text(
            text = stringResource(confirmMessage(confirm.action, isLive = confirm.advert.isLive)),
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurfaceMuted,
        )
        AppButton(
            modifier = Modifier.fillMaxWidth().testTag("advert_confirm_accept"),
            text = stringResource(confirmAction(confirm.action)),
            onClick = onConfirm,
            style = if (confirm.action.isDestructive) {
                AppButtonDefaults.destructive()
            } else {
                AppButtonDefaults.primary()
            },
        )
        AppButton(
            modifier = Modifier.fillMaxWidth().testTag("advert_confirm_cancel"),
            text = stringResource(Res.string.advert_confirm_cancel),
            onClick = onDismiss,
            style = AppButtonDefaults.secondary(),
        )
    }
}

private fun confirmTitle(action: AdvertAction): StringResource = when (action) {
    AdvertAction.Delete -> Res.string.advert_confirm_delete_title
    AdvertAction.Finish -> Res.string.advert_confirm_finish_title
    AdvertAction.Reactivate -> Res.string.advert_confirm_reactivate_title
    AdvertAction.Extend -> Res.string.advert_confirm_extend_title
    // Deactivate routes through the sold prompt, and Edit through the edit sheet, so neither
    // reaches this sheet.
    AdvertAction.Deactivate,
    AdvertAction.Edit -> Res.string.advert_confirm_finish_title
}

private fun confirmMessage(action: AdvertAction, isLive: Boolean): StringResource = when (action) {
    AdvertAction.Delete -> if (isLive) {
        Res.string.advert_confirm_delete_message_active
    } else {
        Res.string.advert_confirm_delete_message
    }

    AdvertAction.Finish -> Res.string.advert_confirm_finish_message
    AdvertAction.Reactivate -> Res.string.advert_confirm_reactivate_message
    AdvertAction.Extend -> Res.string.advert_confirm_extend_message
    AdvertAction.Deactivate,
    AdvertAction.Edit -> Res.string.advert_confirm_finish_message
}

private fun confirmAction(action: AdvertAction): StringResource = when (action) {
    AdvertAction.Delete -> Res.string.advert_confirm_delete_action
    AdvertAction.Finish -> Res.string.advert_confirm_finish_action
    AdvertAction.Reactivate -> Res.string.advert_confirm_reactivate_action
    AdvertAction.Extend -> Res.string.advert_confirm_extend_action
    AdvertAction.Deactivate,
    AdvertAction.Edit -> Res.string.advert_confirm_finish_action
}

/** Live on OLX, so a delete has to deactivate it first. */
private val MyAdvertItem.isLive: Boolean
    get() = status == AdvertStatus.Active || status == AdvertStatus.Limited

@PreviewLightDark
@Composable
private fun AdvertConfirmSheetPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            AdvertConfirmSheet(
                confirm = ActionConfirm(
                    localIndex = 1,
                    advert = MyAdvertItem(
                        id = 1,
                        title = "Nike Air Max 90",
                        status = AdvertStatus.Outdated,
                        url = "",
                        primaryImageUrl = null,
                        priceFormatted = "₴ 1 800",
                        priceValue = 1800,
                        currencyCode = "UAH",
                        createdAt = "",
                        validTo = "",
                    ),
                    action = AdvertAction.Delete,
                ),
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
