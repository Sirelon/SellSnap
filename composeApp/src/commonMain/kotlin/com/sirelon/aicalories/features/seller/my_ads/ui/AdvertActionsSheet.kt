package com.sirelon.sellsnap.features.seller.my_ads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatistics
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.AdvertSheet
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.advert_action_in_progress
import com.sirelon.sellsnap.generated.resources.advert_actions_open_on_olx
import com.sirelon.sellsnap.generated.resources.advert_extend_unavailable
import com.sirelon.sellsnap.generated.resources.advert_stats_empty
import com.sirelon.sellsnap.generated.resources.advert_stats_failed
import com.sirelon.sellsnap.generated.resources.advert_stats_hint_close
import com.sirelon.sellsnap.generated.resources.advert_stats_hint_low_views
import com.sirelon.sellsnap.generated.resources.advert_stats_hint_no_calls
import com.sirelon.sellsnap.generated.resources.advert_stats_observing
import com.sirelon.sellsnap.generated.resources.advert_stats_phone_views
import com.sirelon.sellsnap.generated.resources.advert_stats_title
import com.sirelon.sellsnap.generated.resources.advert_stats_views
import com.sirelon.sellsnap.generated.resources.ic_eye
import com.sirelon.sellsnap.generated.resources.ic_heart
import com.sirelon.sellsnap.generated.resources.ic_share_2
import com.sirelon.sellsnap.generated.resources.ic_smartphone
import com.sirelon.sellsnap.generated.resources.ic_tag
import com.sirelon.sellsnap.generated.resources.my_ads_price_not_set
import com.sirelon.sellsnap.generated.resources.my_ads_untitled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Everything a seller can do with one published listing (SIR-101/103/105).
 *
 * This is the whole surface the Ad lifecycle milestone adds - no new screens, per SIR-101. It
 * also owns "View on OLX", which is what tapping a row used to do directly; keeping it here means
 * the statistics, the expiry and the actions are all reachable from the same tap rather than
 * needing an affordance the icon set has no glyph for.
 */
@Composable
fun AdvertActionsSheet(
    sheet: AdvertSheet,
    onOpenOnOlx: () -> Unit,
    onAction: (AdvertAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("advert_actions_sheet")
            .padding(horizontal = AppDimens.Spacing.xl4)
            .padding(bottom = AppDimens.Spacing.xl5),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
    ) {
        AdvertSummary(advert = sheet.advert)

        statusExplanation(sheet.advert.status)?.let { explanation ->
            StateNote(text = stringResource(explanation), isWarning = true)
        }

        if (sheet.extendUnavailableHere) {
            StateNote(text = stringResource(Res.string.advert_extend_unavailable), isWarning = false)
        }

        StatisticsBlock(sheet = sheet)

        sheet.actions.forEach { action ->
            val isPending = sheet.pendingAction == action
            AppButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("advert_action_${action.name.lowercase()}"),
                text = if (isPending) {
                    stringResource(Res.string.advert_action_in_progress)
                } else {
                    stringResource(actionLabel(action))
                },
                leadingIcon = painterResource(actionIcon(action)),
                // Any action in flight disables all of them: a second command against a status
                // the advert has already left would just come back as an OLX error.
                enabled = sheet.pendingAction == null,
                onClick = { onAction(action) },
                style = if (action.isDestructive) AppButtonDefaults.destructive() else AppButtonDefaults.secondary(),
            )
        }

        if (sheet.advert.canOpen) {
            AppButton(
                modifier = Modifier.fillMaxWidth().testTag("advert_action_open_on_olx"),
                text = stringResource(Res.string.advert_actions_open_on_olx),
                leadingIcon = painterResource(Res.drawable.ic_share_2),
                onClick = onOpenOnOlx,
                style = AppButtonDefaults.outline(),
            )
        }
    }
}

@Composable
private fun AdvertSummary(advert: MyAdvertItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AppDimens.Size.xl17)
                .clip(RoundedCornerShape(AppDimens.BorderRadius.xl2))
                .background(AppTheme.colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (advert.primaryImageUrl.isNullOrBlank()) {
                Icon(
                    painter = painterResource(Res.drawable.ic_tag),
                    contentDescription = null,
                    tint = AppTheme.colors.onSurfaceSoft,
                    modifier = Modifier.size(AppDimens.Size.xl7),
                )
            } else {
                AppAsyncImage(model = advert.primaryImageUrl, modifier = Modifier.fillMaxWidth())
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
        ) {
            Text(
                text = advert.title.takeIf { it.isNotBlank() } ?: stringResource(Res.string.my_ads_untitled),
                style = AppTheme.typography.title,
                color = AppTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = advert.priceFormatted.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.my_ads_price_not_set),
                style = AppTheme.typography.body,
                color = AppTheme.colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(statusLabel(advert.status)),
                    style = AppTheme.typography.caption,
                    color = statusColor(advert.status),
                    fontWeight = FontWeight.SemiBold,
                )
                AdvertExpiryLine(validTo = advert.validTo)
            }
        }
    }
}

/**
 * The three counters, plus a one-line reading of them (SIR-103). Deliberately no chart: the
 * response carries current totals only, and a chart would imply a time series that does not exist.
 */
@Composable
private fun StatisticsBlock(sheet: AdvertSheet) {
    val statistics = sheet.statistics
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl4),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            Text(
                text = stringResource(Res.string.advert_stats_title),
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurfaceMuted,
                fontWeight = FontWeight.SemiBold,
            )

            when {
                sheet.isLoadingStatistics -> Text(
                    text = stringResource(Res.string.advert_action_in_progress),
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurfaceMuted,
                )

                sheet.statisticsFailed || statistics == null -> Text(
                    text = stringResource(Res.string.advert_stats_failed),
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurfaceMuted,
                )

                statistics.isEmpty -> Text(
                    // Absent numbers on a fresh advert are not an error.
                    text = stringResource(Res.string.advert_stats_empty),
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurfaceMuted,
                )

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatisticCell(Res.drawable.ic_eye, Res.string.advert_stats_views, statistics.advertViews)
                        StatisticCell(
                            Res.drawable.ic_smartphone,
                            Res.string.advert_stats_phone_views,
                            statistics.phoneViews,
                        )
                        StatisticCell(
                            Res.drawable.ic_heart,
                            Res.string.advert_stats_observing,
                            statistics.usersObserving,
                        )
                    }
                    statisticsReading(statistics)?.let { reading ->
                        Text(
                            text = stringResource(reading),
                            style = AppTheme.typography.caption,
                            color = AppTheme.colors.onSurfaceMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticCell(icon: DrawableResource, label: StringResource, value: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AppTheme.colors.primary,
            modifier = Modifier.size(AppDimens.Size.xl5),
        )
        Text(
            text = value.toString(),
            style = AppTheme.typography.title,
            color = AppTheme.colors.onSurface,
        )
        Text(
            text = stringResource(label),
            style = AppTheme.typography.caption,
            color = AppTheme.colors.onSurfaceMuted,
        )
    }
}

/**
 * Turns the three counters into the one sentence a seller can act on. This is where the feature
 * earns its place over reading the same numbers on OLX: knowing that 200 views with no phone
 * reveals means the photos or the price are wrong is the part a seller does not already have.
 *
 * Thresholds are deliberately coarse. They separate the three diagnoses in SIR-103 and nothing
 * finer, because there is no per-category baseline to compare against yet.
 */
private fun statisticsReading(statistics: OlxAdvertStatistics): StringResource? = when {
    statistics.advertViews < LowViewsThreshold -> Res.string.advert_stats_hint_low_views
    statistics.phoneViews == 0 && statistics.usersObserving == 0 -> Res.string.advert_stats_hint_no_calls
    else -> Res.string.advert_stats_hint_close
}

private const val LowViewsThreshold = 20

@Composable
private fun StateNote(text: String, isWarning: Boolean) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isWarning) {
            AppTheme.colors.warning.copy(alpha = 0.12f)
        } else {
            AppTheme.colors.surfaceHigh
        },
    ) {
        Text(
            modifier = Modifier.padding(AppDimens.Spacing.xl4),
            text = text,
            style = AppTheme.typography.body,
            color = AppTheme.colors.onSurface,
        )
    }
}

@PreviewLightDark
@Composable
private fun AdvertActionsSheetPreview() {
    AppTheme {
        Surface(color = AppTheme.colors.background) {
            AdvertActionsSheet(
                sheet = AdvertSheet(
                    localIndex = 1,
                    advert = MyAdvertItem(
                        id = 1,
                        title = "Nike Air Max 90, size 42, worn 2 months",
                        status = AdvertStatus.Active,
                        url = "https://www.olx.ua/d/obyavlenie/x.html",
                        primaryImageUrl = null,
                        priceFormatted = "₴ 1 800",
                        priceValue = 1800,
                        currencyCode = "UAH",
                        createdAt = "2026-08-20T10:00:00+03:00",
                        validTo = "2026-09-05T10:00:00+03:00",
                    ),
                    actions = listOf(AdvertAction.Edit, AdvertAction.Deactivate),
                    extendUnavailableHere = true,
                    statistics = OlxAdvertStatistics(advertViews = 212, phoneViews = 0, usersObserving = 0),
                ),
                onOpenOnOlx = {},
                onAction = {},
            )
        }
    }
}
