package com.sirelon.sellsnap.features.seller.my_ads.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertExpiry
import com.sirelon.sellsnap.features.seller.my_ads.domain.advertExpiryOf
import com.sirelon.sellsnap.features.seller.my_ads.domain.isExpiringSoon
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.advert_action_deactivate
import com.sirelon.sellsnap.generated.resources.advert_action_delete
import com.sirelon.sellsnap.generated.resources.advert_action_edit
import com.sirelon.sellsnap.generated.resources.advert_action_extend
import com.sirelon.sellsnap.generated.resources.advert_action_finish
import com.sirelon.sellsnap.generated.resources.advert_action_reactivate
import com.sirelon.sellsnap.generated.resources.advert_expiry_days_left
import com.sirelon.sellsnap.generated.resources.advert_expiry_expired
import com.sirelon.sellsnap.generated.resources.advert_expiry_today
import com.sirelon.sellsnap.generated.resources.advert_state_blocked
import com.sirelon.sellsnap.generated.resources.advert_state_disabled
import com.sirelon.sellsnap.generated.resources.advert_state_moderated
import com.sirelon.sellsnap.generated.resources.advert_state_new
import com.sirelon.sellsnap.generated.resources.advert_state_removed_by_moderator
import com.sirelon.sellsnap.generated.resources.advert_state_unconfirmed
import com.sirelon.sellsnap.generated.resources.advert_state_unknown
import com.sirelon.sellsnap.generated.resources.advert_state_unpaid
import com.sirelon.sellsnap.generated.resources.ic_circle_check_big
import com.sirelon.sellsnap.generated.resources.ic_pen_line
import com.sirelon.sellsnap.generated.resources.ic_refresh_cw
import com.sirelon.sellsnap.generated.resources.ic_wifi_off
import com.sirelon.sellsnap.generated.resources.ic_x
import com.sirelon.sellsnap.generated.resources.my_ads_status_active
import com.sirelon.sellsnap.generated.resources.my_ads_status_blocked
import com.sirelon.sellsnap.generated.resources.my_ads_status_disabled
import com.sirelon.sellsnap.generated.resources.my_ads_status_limited
import com.sirelon.sellsnap.generated.resources.my_ads_status_moderated
import com.sirelon.sellsnap.generated.resources.my_ads_status_new
import com.sirelon.sellsnap.generated.resources.my_ads_status_outdated
import com.sirelon.sellsnap.generated.resources.my_ads_status_removed_by_moderator
import com.sirelon.sellsnap.generated.resources.my_ads_status_removed_by_user
import com.sirelon.sellsnap.generated.resources.my_ads_status_unconfirmed
import com.sirelon.sellsnap.generated.resources.my_ads_status_unknown
import com.sirelon.sellsnap.generated.resources.my_ads_status_unpaid
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal fun statusLabel(status: AdvertStatus): StringResource = when (status) {
    AdvertStatus.Active -> Res.string.my_ads_status_active
    AdvertStatus.New -> Res.string.my_ads_status_new
    AdvertStatus.Limited -> Res.string.my_ads_status_limited
    AdvertStatus.RemovedByUser -> Res.string.my_ads_status_removed_by_user
    AdvertStatus.Outdated -> Res.string.my_ads_status_outdated
    AdvertStatus.Unconfirmed -> Res.string.my_ads_status_unconfirmed
    AdvertStatus.Unpaid -> Res.string.my_ads_status_unpaid
    AdvertStatus.Moderated -> Res.string.my_ads_status_moderated
    AdvertStatus.Blocked -> Res.string.my_ads_status_blocked
    AdvertStatus.Disabled -> Res.string.my_ads_status_disabled
    AdvertStatus.RemovedByModerator -> Res.string.my_ads_status_removed_by_moderator
    AdvertStatus.Unknown -> Res.string.my_ads_status_unknown
}

@Composable
internal fun statusColor(status: AdvertStatus): Color = when (status) {
    AdvertStatus.Active -> AppTheme.colors.success
    AdvertStatus.New,
    AdvertStatus.Moderated,
    AdvertStatus.Unconfirmed -> AppTheme.colors.warning
    AdvertStatus.Limited,
    AdvertStatus.Unpaid -> AppTheme.colors.warningVariant
    AdvertStatus.RemovedByUser,
    AdvertStatus.Outdated,
    AdvertStatus.Blocked,
    AdvertStatus.Disabled,
    AdvertStatus.RemovedByModerator -> AppTheme.colors.error
    AdvertStatus.Unknown -> AppTheme.colors.primary
}

/**
 * What OLX is doing with a listing the seller cannot act on (SIR-101). Only these statuses have
 * one: for the rest, the actions themselves say what can be done, and a paragraph explaining an
 * actionable state is noise.
 *
 * `RemovedByUser` and `Outdated` are deliberately absent - both are offered Reactivate, Finish
 * and Delete, so nothing needs explaining.
 */
internal fun statusExplanation(status: AdvertStatus): StringResource? = when (status) {
    AdvertStatus.New -> Res.string.advert_state_new
    AdvertStatus.Moderated -> Res.string.advert_state_moderated
    AdvertStatus.Blocked -> Res.string.advert_state_blocked
    AdvertStatus.RemovedByModerator -> Res.string.advert_state_removed_by_moderator
    AdvertStatus.Disabled -> Res.string.advert_state_disabled
    AdvertStatus.Unconfirmed -> Res.string.advert_state_unconfirmed
    AdvertStatus.Unpaid -> Res.string.advert_state_unpaid
    AdvertStatus.Unknown -> Res.string.advert_state_unknown
    AdvertStatus.Active,
    AdvertStatus.Limited,
    AdvertStatus.RemovedByUser,
    AdvertStatus.Outdated -> null
}

internal fun actionLabel(action: AdvertAction): StringResource = when (action) {
    AdvertAction.Edit -> Res.string.advert_action_edit
    AdvertAction.Extend -> Res.string.advert_action_extend
    AdvertAction.Deactivate -> Res.string.advert_action_deactivate
    AdvertAction.Reactivate -> Res.string.advert_action_reactivate
    AdvertAction.Finish -> Res.string.advert_action_finish
    AdvertAction.Delete -> Res.string.advert_action_delete
}

internal fun actionIcon(action: AdvertAction): DrawableResource = when (action) {
    AdvertAction.Edit -> Res.drawable.ic_pen_line
    AdvertAction.Extend -> Res.drawable.ic_refresh_cw
    AdvertAction.Deactivate -> Res.drawable.ic_wifi_off
    AdvertAction.Reactivate -> Res.drawable.ic_refresh_cw
    AdvertAction.Finish -> Res.drawable.ic_circle_check_big
    AdvertAction.Delete -> Res.drawable.ic_x
}

/** Only Delete is genuinely irreversible, so only Delete gets the destructive treatment. */
internal val AdvertAction.isDestructive: Boolean get() = this == AdvertAction.Delete

/**
 * Remaining validity (SIR-105), shared by the list card and the actions sheet so the two cannot
 * style the same state differently - an expired listing is the most urgent expiry state and has
 * to read that way wherever it appears.
 *
 * Renders nothing when `valid_to` was unreadable: a wrong expiry is worse than none.
 */
@Composable
internal fun AdvertExpiryLine(validTo: String, modifier: Modifier = Modifier) {
    val expiry = advertExpiryOf(validTo)
    val text = advertExpiryText(expiry) ?: return
    val isUrgent = expiry is AdvertExpiry.Expired || expiry.isExpiringSoon

    Text(
        modifier = modifier,
        text = text,
        style = AppTheme.typography.caption,
        color = when {
            expiry is AdvertExpiry.Expired -> AppTheme.colors.error
            expiry.isExpiringSoon -> AppTheme.colors.warning
            else -> AppTheme.colors.onSurfaceMuted
        },
        fontWeight = if (isUrgent) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Remaining validity as a sentence, or null when `valid_to` was unreadable. Pluralised through a
 * resource plural rather than a composed string: "3 days left" inflects differently across the
 * Slavic locales this app ships in.
 */
@Composable
private fun advertExpiryText(expiry: AdvertExpiry): String? = when (expiry) {
    AdvertExpiry.Unknown -> null
    AdvertExpiry.Expired -> stringResource(Res.string.advert_expiry_expired)
    is AdvertExpiry.Remaining -> if (expiry.daysLeft == 0) {
        stringResource(Res.string.advert_expiry_today)
    } else {
        pluralStringResource(Res.plurals.advert_expiry_days_left, expiry.daysLeft, expiry.daysLeft)
    }
}
