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
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertState
import com.sirelon.sellsnap.features.seller.my_ads.domain.state
import com.sirelon.sellsnap.features.seller.my_ads.domain.advertExpiryOf
import com.sirelon.sellsnap.features.seller.my_ads.domain.isExpiringSoon
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.advert_action_deactivate
import com.sirelon.sellsnap.generated.resources.advert_action_delete
import com.sirelon.sellsnap.generated.resources.advert_action_edit
import com.sirelon.sellsnap.generated.resources.advert_action_extend
import com.sirelon.sellsnap.generated.resources.advert_action_reactivate
import com.sirelon.sellsnap.generated.resources.advert_expiry_days_left
import com.sirelon.sellsnap.generated.resources.advert_expiry_expired
import com.sirelon.sellsnap.generated.resources.advert_expiry_today
import com.sirelon.sellsnap.generated.resources.advert_state_unknown
import com.sirelon.sellsnap.generated.resources.ic_pen_line
import com.sirelon.sellsnap.generated.resources.ic_refresh_cw
import com.sirelon.sellsnap.generated.resources.ic_wifi_off
import com.sirelon.sellsnap.generated.resources.ic_x
import com.sirelon.sellsnap.generated.resources.advert_state_needs_confirmation
import com.sirelon.sellsnap.generated.resources.advert_state_needs_payment
import com.sirelon.sellsnap.generated.resources.advert_state_rejected
import com.sirelon.sellsnap.generated.resources.my_ads_status_active
import com.sirelon.sellsnap.generated.resources.my_ads_status_inactive
import com.sirelon.sellsnap.generated.resources.my_ads_status_needs_confirmation
import com.sirelon.sellsnap.generated.resources.my_ads_status_needs_payment
import com.sirelon.sellsnap.generated.resources.my_ads_status_rejected
import com.sirelon.sellsnap.generated.resources.my_ads_status_under_review
import com.sirelon.sellsnap.generated.resources.my_ads_status_unknown
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal fun statusLabel(status: AdvertStatus): StringResource = when (status.state) {
    AdvertState.Active -> Res.string.my_ads_status_active
    AdvertState.UnderReview -> Res.string.my_ads_status_under_review
    AdvertState.NeedsPayment -> Res.string.my_ads_status_needs_payment
    AdvertState.NeedsConfirmation -> Res.string.my_ads_status_needs_confirmation
    AdvertState.Rejected -> Res.string.my_ads_status_rejected
    AdvertState.Inactive -> Res.string.my_ads_status_inactive
    AdvertState.Unknown -> Res.string.my_ads_status_unknown
}

/**
 * Three states share amber deliberately: the label carries the distinction between waiting on
 * OLX, on a payment and on a confirmation, and a fourth tint would only raise the question of
 * what the colour means.
 */
@Composable
internal fun statusColor(status: AdvertStatus): Color = when (status.state) {
    AdvertState.Active -> AppTheme.colors.success
    AdvertState.UnderReview,
    AdvertState.NeedsPayment,
    AdvertState.NeedsConfirmation -> AppTheme.colors.warning
    AdvertState.Rejected -> AppTheme.colors.error
    AdvertState.Inactive,
    AdvertState.Unknown -> AppTheme.colors.onSurfaceMuted
}

/**
 * An explanation exists only where the seller has to do something somewhere else - pay, confirm -
 * or where OLX said no. `Active`, `UnderReview` and `Inactive` need none: the badge and the
 * available actions already say everything true about them, and a paragraph restating a badge is
 * noise.
 *
 * For a rejected listing the sheet prefers OLX's own text from `moderation-reason` and falls back
 * to this only when OLX returns nothing.
 */
internal fun statusExplanation(status: AdvertStatus): StringResource? = when (status.state) {
    AdvertState.NeedsPayment -> Res.string.advert_state_needs_payment
    AdvertState.NeedsConfirmation -> Res.string.advert_state_needs_confirmation
    AdvertState.Rejected -> Res.string.advert_state_rejected
    AdvertState.Unknown -> Res.string.advert_state_unknown
    AdvertState.Active,
    AdvertState.UnderReview,
    AdvertState.Inactive -> null
}

internal fun actionLabel(action: AdvertAction): StringResource = when (action) {
    AdvertAction.Edit -> Res.string.advert_action_edit
    AdvertAction.Extend -> Res.string.advert_action_extend
    AdvertAction.Deactivate -> Res.string.advert_action_deactivate
    AdvertAction.Reactivate -> Res.string.advert_action_reactivate
    AdvertAction.Delete -> Res.string.advert_action_delete
}

internal fun actionIcon(action: AdvertAction): DrawableResource = when (action) {
    AdvertAction.Edit -> Res.drawable.ic_pen_line
    AdvertAction.Extend -> Res.drawable.ic_refresh_cw
    AdvertAction.Deactivate -> Res.drawable.ic_wifi_off
    AdvertAction.Reactivate -> Res.drawable.ic_refresh_cw
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
