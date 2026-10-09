package com.sirelon.sellsnap.features.seller.drafts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.features.seller.ad.preview_ad.CopyPill
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.drafts.Draft
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ad_description_label
import com.sirelon.sellsnap.generated.resources.ad_title_label
import com.sirelon.sellsnap.generated.resources.advert_edit_price_label
import com.sirelon.sellsnap.generated.resources.drafts_remove_cd
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.ic_camera
import com.sirelon.sellsnap.generated.resources.ic_trash_2
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

/**
 * One draft: thumbnail, title, price and copy pills for the title, description and price. Tapping
 * the card calls [onOpen]. A non-null [onRemove] replaces the trailing arrow with a remove button.
 *
 * The price is the seller's own when they set one in the preview, otherwise the model's suggestion.
 * Call sites key the card by [Draft.id], so a draft arriving or leaving does not hand its
 * remembered state (a pill's "Copied" flash, the thumbnail's load state) to a neighbour.
 *
 * Not previewable with data: [CopyPill] resolves `Analytics` through Koin, which a preview does
 * not have.
 */
@Composable
internal fun DraftCard(
    draft: Draft,
    currency: OlxCurrency,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    val advertisement = draft.listing.advertisement
    val price = draft.listing.sellerPrice ?: advertisement.suggestedPrice

    AppCard(
        modifier = modifier.fillMaxWidth().testTag("draft_row"),
        onClick = onOpen,
        containerColor = AppTheme.colors.surfaceHigh,
        shape = RoundedCornerShape(AppDimens.BorderRadius.xl7),
        shadowElevation = AppDimens.Size.xxs,
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl4),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DraftThumbnail(imageUrl = advertisement.images.firstOrNull())

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
                ) {
                    Text(
                        text = advertisement.title,
                        style = AppTheme.typography.body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = currency.format(price),
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.onSurfaceMuted,
                    )
                }

                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.testTag("draft_remove")) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_trash_2),
                            contentDescription = stringResource(Res.string.drafts_remove_cd),
                            tint = AppTheme.colors.onSurfaceSoft,
                            modifier = Modifier.size(AppDimens.Size.xl5),
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = AppTheme.colors.onSurfaceSoft,
                        modifier = Modifier.size(AppDimens.Size.xl5),
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
            ) {
                CopyPill(
                    value = advertisement.title,
                    field = "title",
                    label = stringResource(Res.string.ad_title_label),
                    eventName = AnalyticsEvents.DRAFT_COPIED,
                )
                CopyPill(
                    value = advertisement.description,
                    field = "description",
                    label = stringResource(Res.string.ad_description_label),
                    eventName = AnalyticsEvents.DRAFT_COPIED,
                )
                CopyPill(
                    value = price.roundToLong().toString(),
                    field = "price",
                    label = stringResource(Res.string.advert_edit_price_label),
                    eventName = AnalyticsEvents.DRAFT_COPIED,
                )
            }
        }
    }
}

@Composable
private fun DraftThumbnail(imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(AppDimens.Size.xl14)
            .clip(RoundedCornerShape(AppDimens.BorderRadius.xl2))
            .background(AppTheme.colors.surfaceLow),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AppAsyncImage(
                model = imageUrl,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(Res.drawable.ic_camera),
                contentDescription = null,
                tint = AppTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(AppDimens.Size.xl8),
            )
        }
    }
}
