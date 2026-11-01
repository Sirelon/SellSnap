package com.sirelon.sellsnap.features.seller.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.AccountPage
import com.sirelon.sellsnap.features.seller.my_ads.ui.AccountTabLabel
import com.sirelon.sellsnap.generated.resources.messages_select_conversation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.buttons.AppButton
import com.sirelon.sellsnap.designsystem.buttons.AppButtonDefaults
import com.sirelon.sellsnap.designsystem.screens.EmptyScreen
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.messages.domain.RelativeTime
import com.sirelon.sellsnap.features.seller.messages.domain.relativeTime
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.ThreadRow
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesViewModel
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertState
import com.sirelon.sellsnap.features.seller.my_ads.domain.state
import com.sirelon.sellsnap.features.seller.my_ads.ui.StatusChip
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.ic_circle_alert
import com.sirelon.sellsnap.generated.resources.ic_message_circle
import com.sirelon.sellsnap.generated.resources.ic_refresh_cw
import com.sirelon.sellsnap.generated.resources.ic_tag
import com.sirelon.sellsnap.generated.resources.ic_wifi_off
import com.sirelon.sellsnap.generated.resources.messages_connect_description
import com.sirelon.sellsnap.generated.resources.messages_connect_title
import com.sirelon.sellsnap.generated.resources.messages_empty_description
import com.sirelon.sellsnap.generated.resources.messages_empty_title
import com.sirelon.sellsnap.generated.resources.messages_listing_deleted
import com.sirelon.sellsnap.generated.resources.messages_listing_fallback
import com.sirelon.sellsnap.generated.resources.messages_screen_title
import com.sirelon.sellsnap.generated.resources.messages_thread_started
import com.sirelon.sellsnap.generated.resources.my_ads_load_more
import com.sirelon.sellsnap.generated.resources.my_ads_account_fallback_name
import com.sirelon.sellsnap.generated.resources.my_ads_connect_action
import com.sirelon.sellsnap.generated.resources.my_ads_reconnect_description
import com.sirelon.sellsnap.generated.resources.my_ads_reconnect_title
import com.sirelon.sellsnap.generated.resources.profile_reconnect_account_action
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.generated.resources.time_days_ago
import com.sirelon.sellsnap.generated.resources.time_hours_ago
import com.sirelon.sellsnap.generated.resources.time_just_now
import com.sirelon.sellsnap.generated.resources.time_minutes_ago
import com.sirelon.sellsnap.generated.resources.time_yesterday
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

@Composable
fun MessagesScreenRoute(
    onOpenThread: (MessagesContract.Effect.OpenThread) -> Unit,
    onConnectOlxClick: () -> Unit,
    onReconnectClick: () -> Unit,
    /** The conversation shown beside the list on a wide window, highlighted there. */
    selectedThreadId: Long?,
    modifier: Modifier = Modifier,
) {
    val viewModel: MessagesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Polling runs only while this entry is on screen: on a narrow window a pushed conversation
    // takes it out of composition, which fires onStopOrDispose. Beside a conversation it keeps
    // polling.
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(Event.Shown)
        onStopOrDispose { viewModel.onEvent(Event.Hidden) }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is MessagesContract.Effect.OpenThread -> onOpenThread(effect)
            MessagesContract.Effect.ConnectOlx -> onConnectOlxClick()
            MessagesContract.Effect.Reconnect -> onReconnectClick()
        }
    }

    MessagesScreen(state = state, selectedThreadId = selectedThreadId, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun MessagesScreen(
    state: MessagesContract.State,
    selectedThreadId: Long?,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.messages_screen_title)) },
                actions = {
                    if (state.selectedPage?.needsReconnect == false) {
                        IconButton(onClick = { onEvent(Event.RefreshClicked) }) {
                            Icon(painter = painterResource(Res.drawable.ic_refresh_cw), contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        when {
            state.requiresOlxConnection -> EmptyScreen(
                modifier = contentModifier,
                icon = Res.drawable.ic_wifi_off,
                title = stringResource(Res.string.messages_connect_title),
                description = stringResource(Res.string.messages_connect_description),
                actionLabel = stringResource(Res.string.my_ads_connect_action),
                onActionClick = { onEvent(Event.ConnectOlxClicked) },
            )

            state.pages.size <= 1 -> state.pages.firstOrNull()?.let { page ->
                AccountThreads(page = page, selectedThreadId = selectedThreadId, onEvent = onEvent, modifier = contentModifier)
            }

            else -> Column(modifier = contentModifier) {
                AccountTabs(state = state, onEvent = onEvent)

                val initialPage = state.pages
                    .indexOfFirst { it.localIndex == state.selectedLocalIndex }
                    .coerceAtLeast(0)
                val pagerState = rememberPagerState(initialPage = initialPage) { state.pages.size }

                // Same wiring as My Ads: the settled page selects its account, and a selection
                // made elsewhere (a tab tap, a disconnected account) scrolls the pager.
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
                        AccountThreads(
                            page = page,
                            selectedThreadId = selectedThreadId,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTabs(state: MessagesContract.State, onEvent: (Event) -> Unit) {
    val selectedTabIndex = state.pages.indexOfFirst { it.localIndex == state.selectedLocalIndex }.coerceAtLeast(0)
    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
        state.pages.forEachIndexed { index, page ->
            Tab(
                modifier = Modifier.testTag("messages_account_tab"),
                selected = index == selectedTabIndex,
                onClick = { onEvent(Event.PageSelected(page.localIndex)) },
                text = {
                    AccountTabLabel(
                        accountName = page.accountName,
                        isActiveAccount = page.isActiveAccount,
                        needsReconnect = page.needsReconnect,
                    )
                },
            )
        }
    }
}

/** One account's threads, or why there are none. */
@Composable
private fun AccountThreads(
    page: AccountPage,
    selectedThreadId: Long?,
    onEvent: (Event) -> Unit,
    modifier: Modifier,
) {
    when {
        page.needsReconnect -> EmptyScreen(
            modifier = modifier,
            icon = Res.drawable.ic_circle_alert,
            title = stringResource(Res.string.my_ads_reconnect_title),
            description = stringResource(Res.string.my_ads_reconnect_description),
            actionLabel = stringResource(
                Res.string.profile_reconnect_account_action,
                page.accountName?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.my_ads_account_fallback_name),
            ),
            onActionClick = { onEvent(Event.ReconnectClicked) },
        )

        page.errorMessage != null -> EmptyScreen(
            modifier = modifier,
            icon = Res.drawable.ic_circle_alert,
            title = page.errorMessage,
            description = "",
            actionLabel = stringResource(Res.string.retry),
            onActionClick = { onEvent(Event.RefreshClicked) },
        )

        // A full page of only buyer-side threads shows nothing but may be followed by more.
        page.hasLoaded && page.threads.isEmpty() && !page.canLoadMore -> EmptyScreen(
            modifier = modifier,
            icon = Res.drawable.ic_message_circle,
            title = stringResource(Res.string.messages_empty_title),
            description = stringResource(Res.string.messages_empty_description),
            actionLabel = null,
        )

        else -> PullToRefreshBox(
            isRefreshing = page.isLoading && page.hasLoaded,
            onRefresh = { onEvent(Event.RefreshClicked) },
            modifier = modifier,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(AppDimens.Spacing.xl3),
                verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
            ) {
                items(items = page.threads, key = { it.threadId }) { row ->
                    ThreadCard(
                        row = row,
                        isSelected = row.threadId == selectedThreadId,
                        onClick = { onEvent(Event.ThreadClicked(page.localIndex, row)) },
                    )
                }
                if (page.canLoadMore) {
                    item {
                        AppButton(
                            modifier = Modifier.fillMaxWidth().testTag("messages_load_more"),
                            text = stringResource(Res.string.my_ads_load_more),
                            onClick = { onEvent(Event.LoadMoreClicked) },
                            enabled = !page.isLoadingMore,
                            style = AppButtonDefaults.outline(),
                        )
                    }
                }
            }
            // Only the first load blanks the screen; a refresh has its own indicator.
            if (page.isLoading && !page.hasLoaded) {
                LoadingOverlay(isLoading = true) {}
            }
        }
    }
}

/** The detail pane on a wide window while no conversation is open. */
@Composable
fun ConversationPlaceholder() {
    EmptyScreen(
        modifier = Modifier.fillMaxSize().testTag("messages_detail_placeholder"),
        icon = Res.drawable.ic_message_circle,
        title = stringResource(Res.string.messages_select_conversation),
        description = "",
        actionLabel = null,
    )
}

@Composable
private fun ThreadCard(row: ThreadRow, isSelected: Boolean, onClick: () -> Unit) {
    val isUnread = row.unreadCount > 0
    val dimmed = isListingDimmed(row.status, row.isListingDeleted)
    AppCard(
        modifier = Modifier.fillMaxWidth().testTag("messages_thread_row").semantics { selected = isSelected },
        onClick = onClick,
        containerColor = if (isSelected) AppTheme.colors.secondaryContainer else AppTheme.colors.surfaceLowest,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.Spacing.xl4),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListingThumbnail(imageUrl = row.imageUrl, dimmed = dimmed)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s)) {
                Text(
                    text = when {
                        row.isListingDeleted -> stringResource(Res.string.messages_listing_deleted)
                        else -> row.title ?: stringResource(Res.string.messages_listing_fallback)
                    },
                    style = AppTheme.typography.title,
                    fontWeight = if (isUnread) FontWeight.Bold else null,
                    color = if (dimmed) AppTheme.colors.onSurfaceMuted else AppTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // The list endpoint has no last-message time and rows are in last-activity order,
                // so the thread's creation date is labelled as what it is.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.status?.takeIf { it.showsStatusChip() }?.let { StatusChip(status = it) }
                    Text(
                        text = stringResource(
                            Res.string.messages_thread_started,
                            relativeTime(
                                raw = row.createdAt,
                                marketZone = _currentOlxCountry.timeZone,
                                now = Clock.System.now(),
                                zone = TimeZone.currentSystemDefault(),
                            ).asText(),
                        ),
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.onSurfaceMuted,
                        maxLines = 1,
                    )
                }
            }
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .size(AppDimens.Size.xl)
                        .clip(CircleShape)
                        .background(AppTheme.colors.primary)
                        .testTag("messages_unread_dot"),
                )
            }
        }
    }
}

@Composable
private fun RelativeTime.asText(): String = when (this) {
    RelativeTime.JustNow -> stringResource(Res.string.time_just_now)
    is RelativeTime.MinutesAgo -> pluralStringResource(Res.plurals.time_minutes_ago, minutes, minutes)
    is RelativeTime.HoursAgo -> pluralStringResource(Res.plurals.time_hours_ago, hours, hours)
    RelativeTime.Yesterday -> stringResource(Res.string.time_yesterday)
    is RelativeTime.DaysAgo -> pluralStringResource(Res.plurals.time_days_ago, days, days)
    is RelativeTime.Verbatim -> text
}

/**
 * Whether a row or header names its listing's status with a chip. Live and not known have none,
 * and deleted says it in the title. Waiting states stay undimmed: the seller may still have to
 * pay or confirm on OLX.
 */
internal fun AdvertStatus?.showsStatusChip(): Boolean = when (this?.state) {
    AdvertState.UnderReview,
    AdvertState.NeedsPayment,
    AdvertState.NeedsConfirmation,
    AdvertState.Inactive,
    AdvertState.Rejected -> true

    AdvertState.Active,
    AdvertState.Unknown,
    null -> false
}

/**
 * Buyers cannot see the listing and it will not come back on its own. Not known is never dimmed:
 * an unrecognised status could be an active advert.
 */
internal fun isListingDimmed(status: AdvertStatus?, isDeleted: Boolean): Boolean = isDeleted || when (status?.state) {
    AdvertState.Inactive,
    AdvertState.Rejected -> true

    AdvertState.Active,
    AdvertState.UnderReview,
    AdvertState.NeedsPayment,
    AdvertState.NeedsConfirmation,
    AdvertState.Unknown,
    null -> false
}

@Composable
internal fun ListingThumbnail(imageUrl: String?, size: Dp = AppDimens.Size.xl15, dimmed: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (dimmed) DimmedAlpha else 1f)
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
            AppAsyncImage(model = imageUrl, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Matches the disabled-content alpha in `AppButton`; `AppDimens` has no opacity token. */
private const val DimmedAlpha = 0.5f
