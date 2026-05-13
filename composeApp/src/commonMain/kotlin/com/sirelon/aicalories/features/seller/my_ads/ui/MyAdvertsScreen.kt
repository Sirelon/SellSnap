package com.sirelon.sellsnap.features.seller.my_ads.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Event
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsViewModel
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.ic_camera
import com.sirelon.sellsnap.generated.resources.ic_refresh_cw
import com.sirelon.sellsnap.generated.resources.ic_tag
import com.sirelon.sellsnap.generated.resources.ic_wifi_off
import com.sirelon.sellsnap.generated.resources.label_value_format
import com.sirelon.sellsnap.generated.resources.my_ads_connect_action
import com.sirelon.sellsnap.generated.resources.my_ads_connect_description
import com.sirelon.sellsnap.generated.resources.my_ads_connect_title
import com.sirelon.sellsnap.generated.resources.my_ads_create_listing
import com.sirelon.sellsnap.generated.resources.my_ads_created_at
import com.sirelon.sellsnap.generated.resources.my_ads_empty_description
import com.sirelon.sellsnap.generated.resources.my_ads_empty_title
import com.sirelon.sellsnap.generated.resources.my_ads_load_more
import com.sirelon.sellsnap.generated.resources.my_ads_price_not_set
import com.sirelon.sellsnap.generated.resources.my_ads_screen_subtitle
import com.sirelon.sellsnap.generated.resources.my_ads_screen_title
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
import com.sirelon.sellsnap.generated.resources.my_ads_untitled
import com.sirelon.sellsnap.generated.resources.my_ads_valid_to
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.platform.openUrl
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyAdvertsScreenRoute(
    onConnectOlxClick: () -> Unit,
    onCreateListingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MyAdvertsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            MyAdvertsContract.Effect.ConnectOlx -> onConnectOlxClick()
            MyAdvertsContract.Effect.CreateListing -> onCreateListingClick()
            is MyAdvertsContract.Effect.OpenUrl -> openUrl(effect.url)
            is MyAdvertsContract.Effect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    LoadingOverlay(isLoading = state.isLoading) {
        MyAdvertsScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }
}

@Composable
private fun MyAdvertsScreen(
    state: MyAdvertsContract.State,
    snackbarHostState: SnackbarHostState,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.my_ads_screen_title)) },
                actions = {
                    IconButton(onClick = { onEvent(Event.RefreshClicked) }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_refresh_cw),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(horizontal = AppDimens.Spacing.xl3),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            item {
                MyAdsHeader()
            }

            when {
                state.requiresOlxConnection -> item {
                    ConnectionRequiredCard(onConnect = { onEvent(Event.ConnectOlxClicked) })
                }

                state.adverts.isEmpty() && !state.isLoading && state.errorMessage == null -> item {
                    EmptyAdsCard(onCreateListing = { onEvent(Event.CreateListingClicked) })
                }

                else -> {
                    items(
                        items = state.adverts,
                        key = { it.id },
                    ) { advert ->
                        AdvertCard(
                            advert = advert,
                            onClick = { onEvent(Event.AdvertClicked(advert)) },
                        )
                    }

                    if (state.canLoadMore) {
                        item {
                            AppButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(Res.string.my_ads_load_more),
                                onClick = { onEvent(Event.LoadMoreClicked) },
                                enabled = !state.isLoadingMore,
                                style = AppButtonDefaults.outline(),
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                item {
                    ErrorCard(
                        message = message,
                        onRetry = { onEvent(Event.RefreshClicked) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(AppDimens.Spacing.xl3))
            }
        }
    }
}

@Composable
private fun MyAdsHeader(modifier: Modifier = Modifier) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = AppTheme.colors.primary.copy(alpha = 0.12f),
        contentColor = AppTheme.colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(AppDimens.Spacing.xl5),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.Size.xl12)
                    .clip(CircleShape)
                    .background(AppTheme.colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_tag),
                    contentDescription = null,
                    tint = AppTheme.colors.onPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs)) {
                Text(
                    text = stringResource(Res.string.my_ads_screen_title),
                    style = AppTheme.typography.title,
                    color = AppTheme.colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.my_ads_screen_subtitle),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun ConnectionRequiredCard(onConnect: () -> Unit) {
    StateCard(
        icon = Res.drawable.ic_wifi_off,
        title = stringResource(Res.string.my_ads_connect_title),
        description = stringResource(Res.string.my_ads_connect_description),
        actionText = stringResource(Res.string.my_ads_connect_action),
        onAction = onConnect,
    )
}

@Composable
private fun EmptyAdsCard(onCreateListing: () -> Unit) {
    StateCard(
        icon = Res.drawable.ic_camera,
        title = stringResource(Res.string.my_ads_empty_title),
        description = stringResource(Res.string.my_ads_empty_description),
        actionText = stringResource(Res.string.my_ads_create_listing),
        onAction = onCreateListing,
    )
}

@Composable
private fun StateCard(
    icon: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl5),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(AppDimens.Size.xl12)
                    .clip(RoundedCornerShape(AppDimens.BorderRadius.xl2))
                    .background(AppTheme.colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = AppTheme.colors.primary,
                )
            }
            Text(
                text = title,
                style = AppTheme.typography.title,
                color = AppTheme.colors.onSurface,
            )
            Text(
                text = description,
                style = AppTheme.typography.body,
                color = AppTheme.colors.onSurfaceMuted,
            )
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                text = actionText,
                onClick = onAction,
                style = AppButtonDefaults.primary(),
            )
        }
    }
}

@Composable
private fun AdvertCard(
    advert: MyAdvertItem,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (advert.canOpen) 1f else 0.62f)
                .padding(AppDimens.Spacing.xl4),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdvertThumbnail(advert.primaryImageUrl)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = advert.title.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.my_ads_untitled),
                        modifier = Modifier.weight(1f),
                        style = AppTheme.typography.title,
                        color = AppTheme.colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusChip(status = advert.status)
                }

                Text(
                    text = advert.priceFormatted.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.my_ads_price_not_set),
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                DateLine(
                    label = stringResource(Res.string.my_ads_created_at),
                    value = advert.createdAt,
                )
                DateLine(
                    label = stringResource(Res.string.my_ads_valid_to),
                    value = advert.validTo,
                )
            }

            Icon(
                painter = painterResource(Res.drawable.ic_arrow_right),
                contentDescription = null,
                tint = AppTheme.colors.onSurfaceSoft,
                modifier = Modifier.size(AppDimens.Size.xl5),
            )
        }
    }
}

@Composable
private fun AdvertThumbnail(imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(AppDimens.Size.xl17)
            .clip(RoundedCornerShape(AppDimens.BorderRadius.xl2))
            .background(AppTheme.colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                painter = painterResource(Res.drawable.ic_tag),
                contentDescription = null,
                tint = AppTheme.colors.onSurfaceSoft,
                modifier = Modifier.size(AppDimens.Size.xl7),
            )
        } else {
            AppAsyncImage(
                model = imageUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DateLine(label: String, value: String) {
    if (value.isBlank()) return

    Text(
        text = stringResource(Res.string.label_value_format, label, value),
        style = AppTheme.typography.caption,
        color = AppTheme.colors.onSurfaceMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusChip(status: AdvertStatus) {
    val color = statusColor(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AppDimens.BorderRadius.xl))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = AppDimens.Spacing.m, vertical = AppDimens.Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(statusLabel(status)),
            style = AppTheme.typography.caption,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun statusColor(status: AdvertStatus): Color = when (status) {
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

private fun statusLabel(status: AdvertStatus): StringResource = when (status) {
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
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = AppTheme.colors.error.copy(alpha = 0.10f),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.Spacing.xl4),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
        ) {
            Text(
                text = message,
                style = AppTheme.typography.body,
                color = AppTheme.colors.error,
            )
            AppButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(Res.string.retry),
                onClick = onRetry,
                style = AppButtonDefaults.outline(),
            )
        }
    }
}
