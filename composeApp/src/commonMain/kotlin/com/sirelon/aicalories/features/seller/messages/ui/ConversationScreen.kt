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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.sirelon.sellsnap.designsystem.AppAsyncImage
import com.sirelon.sellsnap.designsystem.AppCard
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppScaffold
import com.sirelon.sellsnap.designsystem.AppTheme
import com.sirelon.sellsnap.designsystem.Input
import com.sirelon.sellsnap.designsystem.ObserveAsEvents
import com.sirelon.sellsnap.designsystem.buttons.AppIconButton
import com.sirelon.sellsnap.designsystem.screens.EmptyScreen
import com.sirelon.sellsnap.designsystem.screens.LoadingOverlay
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.messageTime
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationViewModel
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertItemMapper
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.ui.StatusChip
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.advert_action_failed
import com.sirelon.sellsnap.generated.resources.back
import com.sirelon.sellsnap.generated.resources.conversation_message_not_sent
import com.sirelon.sellsnap.generated.resources.conversation_message_sending
import com.sirelon.sellsnap.generated.resources.conversation_price_floor
import com.sirelon.sellsnap.generated.resources.conversation_price_floor_no_ask
import com.sirelon.sellsnap.generated.resources.conversation_reply_hint
import com.sirelon.sellsnap.generated.resources.conversation_reply_on_olx
import com.sirelon.sellsnap.generated.resources.conversation_scam_warning
import com.sirelon.sellsnap.generated.resources.conversation_send
import com.sirelon.sellsnap.generated.resources.ic_arrow_right
import com.sirelon.sellsnap.generated.resources.ic_circle_alert
import com.sirelon.sellsnap.generated.resources.ic_triangle_alert
import com.sirelon.sellsnap.generated.resources.messages_buyer_label
import com.sirelon.sellsnap.generated.resources.messages_listing_deleted
import com.sirelon.sellsnap.generated.resources.messages_listing_fallback
import com.sirelon.sellsnap.generated.resources.messages_mark_as_read
import com.sirelon.sellsnap.generated.resources.my_ads_account_fallback_name
import com.sirelon.sellsnap.generated.resources.my_ads_price_not_set
import com.sirelon.sellsnap.generated.resources.my_ads_reconnect_description
import com.sirelon.sellsnap.generated.resources.my_ads_reconnect_title
import com.sirelon.sellsnap.generated.resources.profile_reconnect_account_action
import com.sirelon.sellsnap.generated.resources.retry
import com.sirelon.sellsnap.platform.openUrl
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock

@Composable
fun ConversationScreenRoute(
    localIndex: Int?,
    threadId: Long,
    advertId: Long,
    title: String?,
    imageUrl: String?,
    status: AdvertStatus?,
    isListingDeleted: Boolean,
    hadUnread: Boolean,
    onBack: () -> Unit,
    onReconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ConversationViewModel = koinViewModel {
        parametersOf(localIndex, threadId, advertId, title, imageUrl, status, isListingDeleted, hadUnread)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(Event.Shown)
        onStopOrDispose { viewModel.onEvent(Event.Hidden) }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            ConversationContract.Effect.Close -> onBack()
            ConversationContract.Effect.Reconnect -> onReconnectClick()
            is ConversationContract.Effect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    ConversationScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
private fun ConversationScreen(
    state: ConversationContract.State,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEvent: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Beside the list on a wide window the list is the way back, so there is no back arrow.
    val isBesideList = LocalListDetailSceneScope.current != null
    AppScaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.messages_buyer_label)) },
                navigationIcon = {
                    if (!isBesideList) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    }
                },
                actions = {
                    // Without a session the command cannot be sent: the reconnect state is on screen.
                    if (state.canMarkAsRead && !state.needsReconnect) {
                        TextButton(
                            onClick = { onEvent(Event.MarkAsReadClicked) },
                            enabled = !state.isMarkingAsRead,
                            modifier = Modifier.testTag("conversation_mark_as_read"),
                        ) {
                            Text(stringResource(Res.string.messages_mark_as_read))
                        }
                    }
                    // Inferred: the thread deep link is undocumented, so this opens the account's
                    // answers page. The path resolves (200, no redirect) on every olx domain this
                    // app supports.
                    IconButton(
                        onClick = {
                            onEvent(Event.ReplyOnOlxClicked)
                            openUrl("https://www.${_currentOlxCountry.domain}/myaccount/answers/")
                        },
                        modifier = Modifier.testTag("conversation_reply_on_olx"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(Res.string.conversation_reply_on_olx),
                        )
                    }
                },
            )
        },
        bottomBar = {
            // While the account needs a reconnect, the reconnect state above takes the input's
            // place: a reply could only fail.
            if (!state.needsReconnect) {
                ReplyInput(
                    draft = state.draft,
                    canSend = state.canSend,
                    onDraftChange = { onEvent(Event.DraftChanged(it)) },
                    onSend = { onEvent(Event.SendClicked(isTwoPane = isBesideList)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // Sticky by construction: it sits above the list, not inside it.
            ListingHeader(
                listing = state.listing,
                title = state.title,
                imageUrl = state.imageUrl,
                status = state.listing?.status ?: state.status,
                isDeleted = state.isListingDeleted,
                onClick = { url ->
                    onEvent(Event.ListingClicked)
                    openUrl(url)
                },
            )
            state.priceFloor?.let { PriceFloorLine(it) }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.needsReconnect) {
                    EmptyScreen(
                        modifier = Modifier.fillMaxSize(),
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
                } else if (state.errorMessage != null && state.pendingMessages.isEmpty()) {
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
                        items(items = state.pendingMessages.asReversed(), key = { "pending-${it.localId}" }) { pending ->
                            PendingMessageBubble(
                                message = pending,
                                onRetry = { onEvent(Event.RetrySendClicked(pending.localId, isTwoPane = isBesideList)) },
                            )
                        }
                        items(items = state.messages.asReversed(), key = { it.id }) { message ->
                            MessageBubble(message = message, isFlagged = message.id in state.flaggedMessageIds)
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

/** Private to the seller: muted and plain, never a message bubble. */
@Composable
private fun PriceFloorLine(priceFloor: ConversationContract.PriceFloor) {
    val floor = MyAdvertItemMapper.formatAdvertPrice(priceFloor.floor, priceFloor.currencyCode)
    val text = priceFloor.asking
        ?.let { MyAdvertItemMapper.formatAdvertPrice(it, priceFloor.currencyCode) }
        ?.let { stringResource(Res.string.conversation_price_floor, floor, it) }
        ?: stringResource(Res.string.conversation_price_floor_no_ask, floor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl3)
            .padding(bottom = AppDimens.Spacing.m)
            .testTag("conversation_price_floor"),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = AppTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(AppDimens.Size.xl4),
        )
        Text(text = text, style = AppTheme.typography.caption, color = AppTheme.colors.onSurfaceMuted)
    }
}

@Composable
private fun ListingHeader(
    listing: MyAdvertItem?,
    title: String?,
    imageUrl: String?,
    status: AdvertStatus?,
    isDeleted: Boolean,
    onClick: (url: String) -> Unit,
) {
    val dimmed = isListingDimmed(status, isDeleted)
    // Deleted: OLX answers 404 for the advert, so there is no listing to open.
    val url = listing?.url?.takeIf { !isDeleted && it.isNotBlank() }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.m)
            .testTag("conversation_listing_header"),
        onClick = url?.let { { onClick(it) } },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListingThumbnail(
                imageUrl = if (isDeleted) null else listing?.primaryImageUrl ?: imageUrl,
                size = AppDimens.Size.xl12,
                dimmed = dimmed,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isDeleted -> stringResource(Res.string.messages_listing_deleted)
                        else -> listing?.title?.takeIf(String::isNotBlank)
                            ?: title
                            ?: stringResource(Res.string.messages_listing_fallback)
                    },
                    style = AppTheme.typography.title,
                    color = if (dimmed) AppTheme.colors.onSurfaceMuted else AppTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val chipStatus = status?.takeIf { !isDeleted && it.showsStatusChip() }
                if (!isDeleted && (listing != null || chipStatus != null)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                        chipStatus?.let { StatusChip(status = it) }
                    }
                }
            }
            if (url != null) {
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
private fun MessageBubble(message: OlxMessage, isFlagged: Boolean) {
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
            if (isFlagged) ScamWarning()
            Text(
                text = messageTime(
                    raw = message.createdAt,
                    marketZone = _currentOlxCountry.timeZone,
                    now = Clock.System.now(),
                    zone = TimeZone.currentSystemDefault(),
                ),
                style = AppTheme.typography.caption,
                color = textColor.copy(alpha = 0.7f),
            )
        }
    }
}

/** The warning attached to a received message that matches a scam rule (SIR-176). */
@Composable
private fun ScamWarning() {
    Row(
        modifier = Modifier.testTag("conversation_scam_warning"),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_triangle_alert),
            contentDescription = null,
            tint = AppTheme.colors.error,
            modifier = Modifier.size(AppDimens.Size.xl3),
        )
        Text(
            text = stringResource(Res.string.conversation_scam_warning),
            style = AppTheme.typography.caption,
            color = AppTheme.colors.error,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A reply this screen sent that the loaded messages do not show yet. Sending: dimmed. Sent: a plain
 * sent bubble without a time (OLX's answer has none; the next load brings the real one). Failed:
 * the text stays, with OLX's refusal when it gave one, and Retry.
 */
@Composable
private fun PendingMessageBubble(message: ConversationContract.PendingMessage, onRetry: () -> Unit) {
    val textColor = AppTheme.colors.onPrimary
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppDimens.Size.xl24)
                .alpha(if (message.status == ConversationContract.PendingStatus.Sending) 0.6f else 1f)
                .clip(RoundedCornerShape(AppDimens.BorderRadius.xl3))
                .background(AppTheme.colors.primary)
                .padding(AppDimens.Spacing.xl)
                .testTag("conversation_message_pending"),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
        ) {
            Text(text = message.text, style = AppTheme.typography.body, color = textColor)
            if (message.status == ConversationContract.PendingStatus.Sending) {
                Text(
                    text = stringResource(Res.string.conversation_message_sending),
                    style = AppTheme.typography.caption,
                    color = textColor.copy(alpha = 0.7f),
                )
            }
        }
        if (message.status == ConversationContract.PendingStatus.Failed) {
            Row(
                modifier = Modifier.widthIn(max = AppDimens.Size.xl24),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_circle_alert),
                    contentDescription = null,
                    tint = AppTheme.colors.error,
                    modifier = Modifier.size(AppDimens.Size.xl3),
                )
                Text(
                    text = message.olxRefusal
                        ?.let { stringResource(Res.string.advert_action_failed, it) }
                        ?: stringResource(Res.string.conversation_message_not_sent),
                    style = AppTheme.typography.caption,
                    color = AppTheme.colors.error,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag("conversation_message_retry"),
                ) {
                    Text(stringResource(Res.string.retry))
                }
            }
        }
    }
}

/**
 * The reply field and Send. It sits in the scaffold's bottom bar and pads itself above the
 * keyboard and the navigation bar, like the publish bar on the preview screen.
 */
@Composable
private fun ReplyInput(
    draft: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = AppDimens.Spacing.xl3, vertical = AppDimens.Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.Spacing.m),
        verticalAlignment = Alignment.Bottom,
    ) {
        Input(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f).testTag("conversation_reply_input"),
            placeholder = stringResource(Res.string.conversation_reply_hint),
            singleLine = false,
            maxLines = 5,
            // Return inserts a line break; Send is the button.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.None,
            ),
        )
        AppIconButton(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Send),
            onClick = onSend,
            enabled = canSend,
            contentDescription = stringResource(Res.string.conversation_send),
            modifier = Modifier.testTag("conversation_send"),
        )
    }
}
