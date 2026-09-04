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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.ErrorPill
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.Pill
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.AccountPage
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Event
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsViewModel
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.account_needs_reconnect_badge
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.ic_camera
import com.sirelon.sellsnap.generated.resources.ic_check
import com.sirelon.sellsnap.generated.resources.ic_circle_alert
import com.sirelon.sellsnap.generated.resources.ic_refresh_cw
import com.sirelon.sellsnap.generated.resources.ic_tag
import com.sirelon.sellsnap.generated.resources.ic_wifi_off
import com.sirelon.sellsnap.generated.resources.label_value_format
import com.sirelon.sellsnap.generated.resources.my_ads_account_fallback_name
import com.sirelon.sellsnap.generated.resources.my_ads_connect_action
import com.sirelon.sellsnap.generated.resources.my_ads_connect_description
import com.sirelon.sellsnap.generated.resources.my_ads_connect_title
import com.sirelon.sellsnap.generated.resources.my_ads_create_listing
import com.sirelon.sellsnap.generated.resources.my_ads_created_at
import com.sirelon.sellsnap.generated.resources.my_ads_empty_description_account
import com.sirelon.sellsnap.generated.resources.my_ads_empty_title
import com.sirelon.sellsnap.generated.resources.my_ads_header_subtitle_account
import com.sirelon.sellsnap.generated.resources.my_ads_load_more
import com.sirelon.sellsnap.generated.resources.my_ads_price_not_set
import com.sirelon.sellsnap.generated.resources.my_ads_reconnect_description
import com.sirelon.sellsnap.generated.resources.my_ads_reconnect_title
import com.sirelon.sellsnap.generated.resources.my_ads_screen_title
import com.sirelon.sellsnap.generated.resources.my_ads_untitled
import com.sirelon.sellsnap.generated.resources.profile_account_active_badge
import com.sirelon.sellsnap.generated.resources.profile_reconnect_account_action
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.platform.openUrl
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyAdvertsScreenRoute(
    onConnectOlxClick: () -> Unit,
    onCreateListingClick: () -> Unit,
    onReconnectClick: () -> Unit,
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
            is MyAdvertsContract.Effect.Reconnect -> onReconnectClick()
        }
    }

    MyAdvertsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
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
                    val selected = state.selectedLocalIndex
                    if (selected != null) {
                        IconButton(onClick = { onEvent(Event.RefreshClicked(selected)) }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_refresh_cw),
                                contentDescription = null,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.requiresOlxConnection -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .padding(horizontal = AppDimens.Spacing.xl3),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
                ) {
                    item { MyAdsHeader(accountName = null) }
                    item { ConnectionRequiredCard(onConnect = { onEvent(Event.ConnectOlxClicked) }) }
                }
            }

            state.pages.size <= 1 -> {
                val page = state.pages.firstOrNull()
                Box(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (page != null) {
                        AccountPageContent(page = page, onEvent = onEvent)
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding),
                ) {
                    AccountTabRow(
                        pages = state.pages,
                        selectedLocalIndex = state.selectedLocalIndex,
                        onPageSelected = { onEvent(Event.PageSelected(it)) },
                    )

                    val initialPage = state.pages
                        .indexOfFirst { it.localIndex == state.selectedLocalIndex }
                        .coerceAtLeast(0)
                    val pagerState = rememberPagerState(initialPage = initialPage) { state.pages.size }

                    // Keyed on pagerState alone so the collector isn't torn down and relaunched
                    // on every page-list change - rememberUpdatedState keeps its read of
                    // state.pages fresh without that, since `state` is a plain parameter that
                    // wouldn't otherwise re-read inside a long-lived effect body.
                    val currentPages by rememberUpdatedState(state.pages)
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
                            currentPages.getOrNull(settledPage)?.let { onEvent(Event.PageSelected(it.localIndex)) }
                        }
                    }

                    LaunchedEffect(state.selectedLocalIndex) {
                        val targetPage = state.pages.indexOfFirst { it.localIndex == state.selectedLocalIndex }
                        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { pageIndex -> state.pages.getOrNull(pageIndex)?.localIndex ?: pageIndex },
                    ) { pageIndex ->
                        state.pages.getOrNull(pageIndex)?.let { page ->
                            AccountPageContent(page = page, onEvent = onEvent)
                        }
                    }
                }
            }
        }
    }

    AdvertSheets(state = state, onEvent = onEvent)
}

/**
 * The lifecycle sheets (SIR-101/102/103/104), rendered inside this screen rather than as
 * `AppKey` destinations like the PreviewAd flow's sheets.
 *
 * Each one needs the tapped [MyAdvertItem] plus per-advert async state - statistics in flight,
 * a pending command, a loaded edit payload. Routing that through the back stack would mean either
 * serializing the item into a nav key or wiring `SharedViewModelStoreNavEntryDecorator` onto the
 * My Ads tab entry to share this ViewModel with sheet entries, for no behavioural gain.
 *
 * Only one is ever open at a time, and the order matters: a confirmation, the sold prompt or the
 * edit form sits *over* the actions sheet, so the actions sheet stays out of the way while one of
 * them is up.
 */
@Composable
private fun AdvertSheets(
    state: MyAdvertsContract.State,
    onEvent: (Event) -> Unit,
) {
    val hasForegroundSheet = state.actionConfirm != null ||
        state.soldPrompt != null ||
        state.advertEdit != null

    state.advertSheet?.takeIf { !hasForegroundSheet }?.let { sheet ->
        ModalBottomSheet(
            onDismissRequest = { onEvent(Event.AdvertSheetDismissed) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AdvertActionsSheet(
                sheet = sheet,
                onOpenOnOlx = { onEvent(Event.OpenOnOlxClicked) },
                onAction = { onEvent(Event.ActionClicked(it)) },
            )
        }
    }

    state.actionConfirm?.let { confirm ->
        ModalBottomSheet(
            onDismissRequest = { onEvent(Event.ActionDismissed) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AdvertConfirmSheet(
                confirm = confirm,
                onConfirm = { onEvent(Event.ActionConfirmed) },
                onDismiss = { onEvent(Event.ActionDismissed) },
            )
        }
    }

    state.soldPrompt?.let { prompt ->
        ModalBottomSheet(
            // Swipe-to-dismiss abandons the take-down entirely: OLX will not accept a
            // `deactivate` without an answer, so half an answer is not a state to be in.
            onDismissRequest = { if (!prompt.isSubmitting) onEvent(Event.SoldPromptDismissed) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            MarkAsSoldSheet(
                prompt = prompt,
                onAnswer = { onEvent(Event.SoldAnswered(it)) },
                onPriceSubmitted = { onEvent(Event.SoldPriceSubmitted(it)) },
            )
        }
    }

    state.advertEdit?.let { edit ->
        ModalBottomSheet(
            onDismissRequest = { if (!edit.isSaving) onEvent(Event.EditDismissed) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AdvertEditSheet(
                edit = edit,
                onSubmit = { title, description, price ->
                    onEvent(Event.EditSubmitted(title, description, price))
                },
                onDismiss = { onEvent(Event.EditDismissed) },
            )
        }
    }
}

@Composable
private fun AccountTabRow(
    pages: List<AccountPage>,
    selectedLocalIndex: Int?,
    onPageSelected: (Int) -> Unit,
) {
    val selectedTabIndex = pages.indexOfFirst { it.localIndex == selectedLocalIndex }.coerceAtLeast(0)
    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
        pages.forEachIndexed { index, page ->
            Tab(
                selected = index == selectedTabIndex,
                onClick = { onPageSelected(page.localIndex) },
                text = { AccountTabLabel(page = page) },
            )
        }
    }
}

@Composable
private fun AccountTabLabel(page: AccountPage) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xs),
    ) {
        Text(
            text = page.accountName.orAccountFallback(),
            style = AppTheme.typography.body,
            color = if (page.needsReconnect) AppTheme.colors.error else AppTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (page.isActiveAccount) {
            Pill(
                text = stringResource(Res.string.profile_account_active_badge),
                iconResource = Res.drawable.ic_check,
                color = AppTheme.colors.primary,
            )
        }
        if (page.needsReconnect) {
            ErrorPill(label = stringResource(Res.string.account_needs_reconnect_badge))
        }
    }
}

@Composable
private fun AccountPageContent(
    page: AccountPage,
    onEvent: (Event) -> Unit,
) {
    // Pull down to refresh, per page. A lifecycle action can change a listing's state on OLX's
    // side after the fact - moderation picking it up, an expiry passing - so the seller needs a
    // way to re-read the list that does not involve finding the toolbar button.
    PullToRefreshBox(
        isRefreshing = page.isLoading,
        onRefresh = { onEvent(Event.RefreshClicked(page.localIndex)) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.Spacing.xl3),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
        ) {
            item {
                MyAdsHeader(accountName = page.accountName)
            }

            when {
                page.needsReconnect -> item {
                    ReconnectRequiredCard(
                        accountName = page.accountName,
                        onReconnect = { onEvent(Event.ReconnectClicked(page.localIndex)) },
                    )
                }

                page.adverts.isEmpty() && !page.isLoading && page.errorMessage == null -> item {
                    EmptyAdsCard(
                        accountName = page.accountName,
                        onCreateListing = { onEvent(Event.CreateListingClicked) },
                    )
                }

                else -> {
                    items(
                        items = page.adverts,
                        key = { it.id },
                    ) { advert ->
                        AdvertCard(
                            advert = advert,
                            onClick = { onEvent(Event.AdvertClicked(page.localIndex, advert)) },
                        )
                    }

                    if (page.canLoadMore) {
                        item {
                            AppButton(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(Res.string.my_ads_load_more),
                                onClick = { onEvent(Event.LoadMoreClicked(page.localIndex)) },
                                enabled = !page.isLoadingMore,
                                style = AppButtonDefaults.outline(),
                            )
                        }
                    }
                }
            }

            page.errorMessage?.let { message ->
                item {
                    ErrorCard(
                        message = message,
                        onRetry = { onEvent(Event.RefreshClicked(page.localIndex)) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(AppDimens.Spacing.xl3))
            }
        }

        // Only the first load blanks the page; a pull-to-refresh has its own indicator and must
        // not hide the list the seller is looking at.
        if (page.isLoading && !page.hasLoaded) {
            LoadingOverlay(isLoading = true) {}
        }
    }
}

@Composable
private fun MyAdsHeader(accountName: String?, modifier: Modifier = Modifier) {
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
                    text = stringResource(
                        Res.string.my_ads_header_subtitle_account,
                        accountName.orAccountFallback(),
                    ),
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
private fun EmptyAdsCard(accountName: String?, onCreateListing: () -> Unit) {
    StateCard(
        icon = Res.drawable.ic_camera,
        title = stringResource(Res.string.my_ads_empty_title),
        description = stringResource(
            Res.string.my_ads_empty_description_account,
            accountName.orAccountFallback(),
        ),
        actionText = stringResource(Res.string.my_ads_create_listing),
        onAction = onCreateListing,
    )
}

@Composable
private fun ReconnectRequiredCard(accountName: String?, onReconnect: () -> Unit) {
    StateCard(
        icon = Res.drawable.ic_circle_alert,
        title = stringResource(Res.string.my_ads_reconnect_title),
        description = stringResource(Res.string.my_ads_reconnect_description),
        actionText = stringResource(Res.string.profile_reconnect_account_action, accountName.orAccountFallback()),
        onAction = onReconnect,
    )
}

/** Falls back to a generic label (SIR-83 D8) when the account has no name yet - either the
 * account's cached profile hasn't loaded, or the account itself has a blank name. */
@Composable
private fun String?.orAccountFallback(): String =
    this?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.my_ads_account_fallback_name)

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
        modifier = Modifier.fillMaxWidth().testTag("my_ads_advert_card"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                AdvertExpiryLine(validTo = advert.validTo)
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
