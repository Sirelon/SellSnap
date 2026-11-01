package com.sirelon.sellsnap.features.seller.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
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
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.formatOlxWallClock
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationViewModel
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.back
import com.sirelon.sellsnap.generated.resources.conversation_reply_on_olx
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.ic_circle_alert
import com.sirelon.sellsnap.generated.resources.messages_buyer_label
import com.sirelon.sellsnap.generated.resources.messages_listing_fallback
import com.sirelon.sellsnap.generated.resources.my_ads_price_not_set
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.platform.openUrl
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ConversationScreenRoute(
    threadId: Long,
    advertId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ConversationViewModel = koinViewModel { parametersOf(threadId, advertId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(Event.Shown)
        onStopOrDispose { viewModel.onEvent(Event.Hidden) }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            ConversationContract.Effect.Close -> onBack()
        }
    }

    ConversationScreen(state = state, onBack = onBack, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
private fun ConversationScreen(
    state: ConversationContract.State,
    onBack: () -> Unit,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.messages_buyer_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Inferred: the thread deep link is undocumented, so this opens the account's answers
            // page. The path resolves (200, no redirect) on every olx domain this app supports.
            AppButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(AppDimens.Spacing.xl3)
                    .testTag("conversation_reply_on_olx"),
                text = stringResource(Res.string.conversation_reply_on_olx),
                onClick = { openUrl("https://www.${_currentOlxCountry.domain}/myaccount/answers/") },
                style = AppButtonDefaults.outline(),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // Sticky by construction: it sits above the list, not inside it.
            ListingHeader(listing = state.listing)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.errorMessage != null) {
                    EmptyScreen(
                        modifier = Modifier.fillMaxSize(),
                        icon = Res.drawable.ic_circle_alert,
                        title = state.errorMessage,
                        description = "",
                        actionLabel = stringResource(Res.string.retry),
                        onActionClick = { onEvent(Event.RetryClicked) },
                    )
                } else {
                    // Newest at the bottom: the list is reversed so it stays anchored there.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(AppDimens.Spacing.xl3),
                        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                    ) {
                        items(items = state.messages.asReversed(), key = { it.id }) { message ->
                            MessageBubble(message = message)
                        }
                    }
                    if (state.isLoading && !state.hasLoaded) {
                        LoadingOverlay(isLoading = true) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingHeader(listing: MyAdvertItem?) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.m)
            .testTag("conversation_listing_header"),
        onClick = listing?.url?.takeIf(String::isNotBlank)?.let { url -> { openUrl(url) } },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListingThumbnail(imageUrl = listing?.primaryImageUrl, size = AppDimens.Size.xl12)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listing?.title?.takeIf(String::isNotBlank)
                        ?: stringResource(Res.string.messages_listing_fallback),
                    style = AppTheme.typography.title,
                    color = AppTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (listing != null) {
                    Text(
                        text = listing.priceFormatted.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.my_ads_price_not_set),
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            if (listing != null) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = AppTheme.colors.onSurfaceSoft,
                    modifier = Modifier.size(AppDimens.Size.xl5),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: OlxMessage) {
    val alignment = if (message.isSent) Alignment.End else Alignment.Start
    val textColor = if (message.isSent) AppTheme.colors.onPrimary else AppTheme.colors.onSurface
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppDimens.Size.xl24)
                .clip(RoundedCornerShape(AppDimens.BorderRadius.xl3))
                .background(
                    if (message.isSent) AppTheme.colors.primary else AppTheme.colors.surfaceLowest,
                )
                .padding(AppDimens.Spacing.xl)
                .testTag(if (message.isSent) "conversation_message_sent" else "conversation_message_received"),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
        ) {
            if (message.text.isNotBlank()) {
                Text(text = message.text, style = AppTheme.typography.body, color = textColor)
            }
            message.attachmentUrls.forEach { url ->
                AppAsyncImage(
                    model = url,
                    modifier = Modifier
                        .size(AppDimens.Size.xl22)
                        .clip(RoundedCornerShape(AppDimens.BorderRadius.xl2))
                        .clickable { openUrl(url) },
                )
            }
            Text(
                text = formatOlxWallClock(message.createdAt),
                style = AppTheme.typography.caption,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    }
}
