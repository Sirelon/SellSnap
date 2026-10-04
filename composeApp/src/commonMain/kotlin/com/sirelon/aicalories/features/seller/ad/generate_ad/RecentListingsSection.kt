package com.sirelon.sellsnap.features.seller.ad.generate_ad

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
import com.sirelon.sellsnap.designsystem.AppSectionHeader
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.features.seller.ad.preview_ad.CopyPill
import com.sirelon.sellsnap.features.seller.ad.recent.RecentListing
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ad_description_label
import com.sirelon.sellsnap.generated.resources.ad_title_label
import com.sirelon.sellsnap.generated.resources.advert_edit_price_label
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.ic_camera
import com.sirelon.sellsnap.generated.resources.recent_listings_section_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToLong

/**
 * The listings generated before, newest first, each with copy pills for its title, description
 * and price. At most `MAX_RECENT_LISTINGS` rows, so a plain [Column] rather than a nested lazy
 * list inside the screen's `LazyColumn`. Not previewable with data: [CopyPill] resolves
 * `Analytics` through Koin, which a preview does not have.
 */
@Composable
internal fun RecentListingsSection(
    listings: List<RecentListing>,
    currency: OlxCurrency,
    onOpen: (RecentListing) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("recent_listings_section"),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l),
    ) {
        AppSectionHeader(title = stringResource(Res.string.recent_listings_section_title))
        listings.forEach { listing ->
            // Keyed so a new generation prepending a row does not hand this row's remembered
            // state (a pill's "Copied" flash, the thumbnail's load state) to its neighbour.
            key(listing.id) {
                RecentListingCard(
                    listing = listing,
                    currency = currency,
                    onOpen = { onOpen(listing) },
                )
            }
        }
    }
}

@Composable
private fun RecentListingCard(
    listing: RecentListing,
    currency: OlxCurrency,
    onOpen: () -> Unit,
) {
    val advertisement = listing.listing.advertisement

    AppCard(
        modifier = Modifier.fillMaxWidth().testTag("recent_listing_row"),
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
                RecentListingThumbnail(imageUrl = advertisement.images.firstOrNull())

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
                        text = currency.format(advertisement.suggestedPrice),
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.onSurfaceMuted,
                    )
                }

                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = AppTheme.colors.onSurfaceSoft,
                    modifier = Modifier.size(AppDimens.Size.xl5),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
            ) {
                CopyPill(
                    value = advertisement.title,
                    field = "title",
                    label = stringResource(Res.string.ad_title_label),
                    eventName = AnalyticsEvents.RECENT_LISTING_COPIED,
                )
                CopyPill(
                    value = advertisement.description,
                    field = "description",
                    label = stringResource(Res.string.ad_description_label),
                    eventName = AnalyticsEvents.RECENT_LISTING_COPIED,
                )
                CopyPill(
                    value = advertisement.suggestedPrice.roundToLong().toString(),
                    field = "price",
                    label = stringResource(Res.string.advert_edit_price_label),
                    eventName = AnalyticsEvents.RECENT_LISTING_COPIED,
                )
            }
        }
    }
}

@Composable
private fun RecentListingThumbnail(imageUrl: String?) {
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
