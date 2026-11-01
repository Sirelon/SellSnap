package com.sirelon.sellsnap.features.seller.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.sirelon.sellsnap.designsystem.screens.EmptyScreen
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.seller.messages.domain.formatOlxWallClock
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.ThreadRow
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesViewModel
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
import com.sirelon.sellsnap.generated.resources.messages_listing_fallback
import com.sirelon.sellsnap.generated.resources.messages_screen_title
import com.sirelon.sellsnap.generated.resources.my_ads_account_fallback_name
import com.sirelon.sellsnap.generated.resources.my_ads_connect_action
import com.sirelon.sellsnap.generated.resources.my_ads_reconnect_description
import com.sirelon.sellsnap.generated.resources.my_ads_reconnect_title
import com.sirelon.sellsnap.generated.resources.profile_reconnect_account_action
import com.sirelon.sellsnap.generated.resources.retry
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MessagesScreenRoute(
    onOpenThread: (threadId: Long, advertId: Long) -> Unit,
    onConnectOlxClick: () -> Unit,
    onReconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MessagesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Polling runs only while this entry is on screen: a pushed conversation takes it out of
    // composition (single-pane scene), which fires onStopOrDispose.
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(Event.Shown)
        onStopOrDispose { viewModel.onEvent(Event.Hidden) }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is MessagesContract.Effect.OpenThread -> onOpenThread(effect.threadId, effect.advertId)
            MessagesContract.Effect.ConnectOlx -> onConnectOlxClick()
            MessagesContract.Effect.Reconnect -> onReconnectClick()
        }
    }

    MessagesScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun MessagesScreen(
    state: MessagesContract.State,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.messages_screen_title)) },
                actions = {
                    if (state.localIndex != null && !state.needsReconnect) {
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

            state.needsReconnect -> EmptyScreen(
                modifier = contentModifier,
                icon = Res.drawable.ic_circle_alert,
                title = stringResource(Res.string.my_ads_reconnect_title),
                description = stringResource(Res.string.my_ads_reconnect_description),
                actionLabel = stringResource(
                    Res.string.profile_reconnect_account_action,
                    state.accountName?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.my_ads_account_fallback_name),
                ),
                onActionClick = { onEvent(Event.ReconnectClicked) },
            )

            state.errorMessage != null -> EmptyScreen(
                modifier = contentModifier,
                icon = Res.drawable.ic_circle_alert,
                title = state.errorMessage,
                description = "",
                actionLabel = stringResource(Res.string.retry),
                onActionClick = { onEvent(Event.RefreshClicked) },
            )

            state.hasLoaded && state.threads.isEmpty() -> EmptyScreen(
                modifier = contentModifier,
                icon = Res.drawable.ic_message_circle,
                title = stringResource(Res.string.messages_empty_title),
                description = stringResource(Res.string.messages_empty_description),
                actionLabel = null,
            )

            else -> PullToRefreshBox(
                isRefreshing = state.isLoading && state.hasLoaded,
                onRefresh = { onEvent(Event.RefreshClicked) },
                modifier = contentModifier,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(AppDimens.Spacing.xl3),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
                ) {
                    items(items = state.threads, key = { it.threadId }) { row ->
                        ThreadCard(row = row, onClick = { onEvent(Event.ThreadClicked(row)) })
                    }
                }
                // Only the first load blanks the screen; a refresh has its own indicator.
                if (state.isLoading && !state.hasLoaded) {
                    LoadingOverlay(isLoading = true) {}
                }
            }
        }
    }
}

@Composable
private fun ThreadCard(row: ThreadRow, onClick: () -> Unit) {
    val isUnread = row.unreadCount > 0
    AppCard(
        modifier = Modifier.fillMaxWidth().testTag("messages_thread_row"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.Spacing.xl4),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListingThumbnail(imageUrl = row.imageUrl)
            Text(
                text = row.title ?: stringResource(Res.string.messages_listing_fallback),
                modifier = Modifier.weight(1f),
                style = AppTheme.typography.title,
                fontWeight = if (isUnread) FontWeight.Bold else null,
                color = AppTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatOlxWallClock(row.createdAt),
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.onSurfaceMuted,
                    maxLines = 1,
                )
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
}

@Composable
internal fun ListingThumbnail(imageUrl: String?, size: Dp = AppDimens.Size.xl15) {
    Box(
        modifier = Modifier
            .size(size)
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
