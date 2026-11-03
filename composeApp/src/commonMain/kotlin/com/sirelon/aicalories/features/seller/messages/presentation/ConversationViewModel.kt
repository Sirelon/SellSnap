package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.analytics.reportShownError
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.shownErrorCause
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.messages.data.activeAccount
import com.sirelon.sellsnap.features.seller.messages.data.countryAccounts
import com.sirelon.sellsnap.features.seller.messages.domain.MessageSource
import com.sirelon.sellsnap.features.seller.messages.domain.MessagesAnalyticsBuckets
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Effect
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.PendingMessage
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.PendingStatus
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.State
import com.sirelon.sellsnap.features.seller.my_ads.data.AccountNeedsReconnect
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.conversation_load_failed
import com.sirelon.sellsnap.generated.resources.generic_error_retry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.time.TimeSource

/**
 * One open conversation. [title], [imageUrl], [status] and [isListingDeleted] come
 * from the list row that opened it.
 * Loads the messages of [threadId] with the token of the account at [keyLocalIndex] - or, for a
 * key saved without one, the account that was active when it opened - whichever account is active
 * now. It shows the reconnect state while that account needs one, and closes itself if that
 * account is disconnected. As on the list, a failed load is shown (and reported) only while there
 * are no messages to show.
 *
 * [hadUnread] is whether the list row had unread messages when it was tapped: it is what offers
 * "Mark as read", which is the only thing that clears a thread's unread count (OLX does not clear
 * it when the messages are read) and runs only when the seller taps it, never on open or poll. A
 * failed command is shown as a snackbar and leaves the action to try again. [timeSource] times the
 * actions for analytics and changes nothing on screen.
 *
 * A reply is sent with the same account, never the active one. Send clears the draft and adds a
 * pending bubble at once; OLX's answer carries no message, so a success re-reads the thread, and
 * the bubble stays until a load shows the reply (see [withoutDelivered]). A failure keeps the
 * bubble and its text with Retry. Nothing is withheld on the listing's state: OLX's refusal, if
 * any, is what the seller sees. Sending does not mark the thread read.
 */
class ConversationViewModel internal constructor(
    keyLocalIndex: Int?,
    private val threadId: Long,
    private val advertId: Long,
    private val title: String?,
    private val imageUrl: String?,
    private val status: AdvertStatus?,
    private val isListingDeleted: Boolean,
    private val hadUnread: Boolean = false,
    private val repository: MessagesRepository,
    private val myAdvertsRepository: MyAdvertsRepository,
    private val accountRepository: SellerAccountRepository,
    private val analytics: Analytics,
    timeSource: TimeSource = TimeSource.Monotonic,
) : BaseViewModel<State, Event, Effect>() {

    private val openedAt = timeSource.markNow()
    private var loadedLogged = false

    /** The thread's account. Null only while a key saved without one waits for an active account. */
    private var localIndex: Int? = keyLocalIndex
    private var isShown = false
    private var pollJob: Job? = null
    private var listingRequested = false
    private var accountSeen = false
    private var closed = false
    private var nextPendingId = 0L

    /** Every message id a load has returned, so each load can tell which sent messages are new. */
    private val seenMessageIds = mutableSetOf<Long>()

    init {
        accountRepository
            .accountsRecordFlow
            .onEach(::onAccounts)
            .launchIn(viewModelScope)
    }

    override fun initialState(): State = State(
        title = title,
        imageUrl = imageUrl,
        status = status,
        isListingDeleted = isListingDeleted,
        canMarkAsRead = hadUnread,
    )

    override fun onEvent(event: Event) {
        when (event) {
            Event.Shown -> {
                isShown = true
                startPolling()
            }

            Event.Hidden -> {
                isShown = false
                pollJob?.cancel()
            }

            Event.RetryClicked -> localIndex?.let { index -> viewModelScope.launch { loadMessages(index, silent = false) } }
            Event.ReconnectClicked -> postEffect(Effect.Reconnect)
            Event.ReplyOnOlxClicked -> logAction("reply_on_olx")
            Event.ListingClicked -> logAction("open_listing")
            Event.MarkAsReadClicked -> markAsRead()
            is Event.DraftChanged -> setState { it.copy(draft = event.text) }
            is Event.SendClicked -> send(event.isTwoPane)
            is Event.RetrySendClicked -> retrySend(event.localId, event.isTwoPane)
        }
    }

    private fun send(isTwoPane: Boolean) {
        val index = localIndex ?: return
        val state = currentState()
        val text = state.draft.trim()
        // The input is not on screen while the account needs a reconnect; a send would only fail.
        if (text.isEmpty() || state.needsReconnect) return
        val pending = PendingMessage(localId = nextPendingId++, text = text, status = PendingStatus.Sending)
        setState { it.copy(draft = "", pendingMessages = it.pendingMessages + pending) }
        deliver(index, pending, isRetry = false, isTwoPane = isTwoPane)
    }

    private fun retrySend(localId: Long, isTwoPane: Boolean) {
        val index = localIndex ?: return
        val state = currentState()
        if (state.needsReconnect) return
        val pending = state.pendingMessages
            .find { it.localId == localId && it.status == PendingStatus.Failed }
            ?: return
        updatePending(localId) { it.copy(status = PendingStatus.Sending, olxRefusal = null) }
        deliver(index, pending, isRetry = true, isTwoPane = isTwoPane)
    }

    private fun deliver(index: Int, pending: PendingMessage, isRetry: Boolean, isTwoPane: Boolean) {
        viewModelScope.launch {
            try {
                repository.sendMessage(index, threadId, pending.text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val refusal = ((error as? OlxApiException)?.error as? OlxApiError.ValidationError)
                    ?.let { "${it.field}: ${it.fieldDetail}" }
                updatePending(pending.localId) { it.copy(status = PendingStatus.Failed, olxRefusal = refusal) }
                logMessageSent(result = "failed", isRetry = isRetry, isTwoPane = isTwoPane)
                // A dead token is not this bubble's error: onAccounts puts the reconnect state on
                // screen, as it does for a failed load.
                if (error !is AccountNeedsReconnect) {
                    analytics.reportShownError(
                        screen = "conversation",
                        reason = "send_failed:${error.shownErrorCause}",
                        throwable = error,
                    )
                }
                return@launch
            }
            updatePending(pending.localId) { it.copy(status = PendingStatus.Sent) }
            logMessageSent(result = "success", isRetry = isRetry, isTwoPane = isTwoPane)
            // Re-read now. Restarting the poll also cancels a read that started before OLX had the
            // reply, so that read cannot land after this one and hide the reply until the next poll.
            startPolling()
        }
    }

    /** A no-op when a load has already shown the reply and dropped its bubble. */
    private fun updatePending(localId: Long, transform: (PendingMessage) -> PendingMessage) {
        setState { state ->
            state.copy(
                pendingMessages = state.pendingMessages.map { if (it.localId == localId) transform(it) else it },
            )
        }
    }

    /**
     * [pending] minus the replies [messages] now shows. A sent message no earlier load returned is
     * one of these replies when its text matches, whatever the bubble's status: a reply still in
     * flight, or one whose answer was lost, is on OLX all the same.
     * Inferred: OLX stores the text as sent; no live reply was checked (SIR-95). If it does not, a
     * new sent message left unmatched takes the oldest bubble OLX accepted, so an accepted reply
     * is never drawn twice.
     */
    private fun withoutDelivered(pending: List<PendingMessage>, messages: List<OlxMessage>): List<PendingMessage> {
        val newSent = messages.filter { it.isSent && it.id !in seenMessageIds }
        messages.forEach { seenMessageIds += it.id }
        if (pending.isEmpty() || newSent.isEmpty()) return pending

        val remaining = pending.toMutableList()
        val unmatched = newSent.filter { message ->
            val match = remaining.indexOfFirst { it.text == message.text.trim() }
            if (match >= 0) remaining.removeAt(match)
            match < 0
        }
        repeat(unmatched.size) {
            val match = remaining.indexOfFirst { it.status == PendingStatus.Sent }
            if (match >= 0) remaining.removeAt(match)
        }
        return remaining
    }

    private fun logMessageSent(result: String, isRetry: Boolean, isTwoPane: Boolean) {
        analytics.logEvent(
            AnalyticsEvents.MESSAGE_SENT,
            mapOf(
                "source" to MessageSource.Typed.code,
                "result" to result,
                "is_retry" to isRetry,
                "layout" to if (isTwoPane) "two_pane" else "single_pane",
                "account_count" to accountRepository.accountsRecordFlow.value.countryAccounts().size,
            ),
        )
    }

    private fun markAsRead() {
        val index = localIndex ?: return
        val state = currentState()
        if (!state.canMarkAsRead || state.isMarkingAsRead) return
        setState { it.copy(isMarkingAsRead = true) }
        viewModelScope.launch {
            try {
                repository.markAsRead(index, threadId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logAction("mark_as_read", result = "failed")
                analytics.reportShownError(
                    screen = "conversation",
                    reason = "mark_read_failed:${error.shownErrorCause}",
                    throwable = error,
                )
                val message = getString(Res.string.generic_error_retry)
                setState { it.copy(isMarkingAsRead = false) }
                postEffect(Effect.ShowMessage(message))
                return@launch
            }
            setState { it.copy(isMarkingAsRead = false, canMarkAsRead = false) }
            logAction("mark_as_read", result = "success")
        }
    }

    private fun logAction(action: String, result: String? = null) {
        val state = currentState()
        analytics.logEvent(
            AnalyticsEvents.MESSAGES_CONVERSATION_ACTION,
            listOfNotNull(
                "action" to action,
                result?.let { "result" to it },
                "listing_state" to MessagesAnalyticsBuckets.listingState(
                    status = state.listing?.status ?: state.status,
                    isDeleted = state.isListingDeleted,
                ),
                "had_unread" to hadUnread,
                "seconds_since_open_bucket" to
                    MessagesAnalyticsBuckets.secondsSinceOpen(openedAt.elapsedNow().inWholeSeconds),
            ).toMap(),
        )
    }

    private fun onAccounts(record: OlxAccountsRecord) {
        val index = localIndex ?: record.activeAccount()?.localIndex ?: return
        val account = record.accounts.find { it.localIndex == index }
        if (account == null) {
            // Only an account seen before can disappear: a stack restored after process death
            // opens before the account store has loaded, and sees no accounts at first.
            if (accountSeen && !closed) {
                pollJob?.cancel()
                postEffect(Effect.Close)
                closed = true
            }
            return
        }
        accountSeen = true
        localIndex = index
        val needsReconnect = account.state == OlxAccountState.NeedsReconnect
        val changed = needsReconnect != currentState().needsReconnect
        setState { it.copy(needsReconnect = needsReconnect, accountName = account.profile?.name) }
        if (needsReconnect) pollJob?.cancel() else if (changed || pollJob == null) startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        val index = localIndex ?: return
        if (!isShown || !accountSeen || currentState().needsReconnect) return

        loadListingOnce(index)
        pollJob = viewModelScope.launch {
            loadMessages(index, silent = currentState().hasLoaded)
            while (true) {
                delay(PollInterval)
                loadMessages(index, silent = true)
            }
        }
    }

    /**
     * One advert request per open conversation, none when the row already knows the advert is
     * deleted. A 404 marks the listing deleted; any other failure leaves the header on what the
     * row passed in.
     */
    private fun loadListingOnce(index: Int) {
        if (listingRequested) return
        listingRequested = true
        if (isListingDeleted) return
        viewModelScope.launch {
            val listing = try {
                myAdvertsRepository.loadAdvertOrNull(index, advertId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            setState { it.copy(listing = listing, isListingDeleted = listing == null) }
        }
    }

    private suspend fun loadMessages(index: Int, silent: Boolean) {
        if (!silent) setState { it.copy(isLoading = true, errorMessage = null) }

        val messages = try {
            repository.loadMessages(index, threadId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleFailure(error)
            return
        }
        // Computed and set with no suspension between, so a bubble added meanwhile is not lost.
        val pending = withoutDelivered(currentState().pendingMessages, messages)
        setState {
            it.copy(
                isLoading = false,
                hasLoaded = true,
                messages = messages,
                errorMessage = null,
                pendingMessages = pending,
            )
        }
        if (!loadedLogged) {
            loadedLogged = true
            analytics.logEvent(
                AnalyticsEvents.MESSAGES_CONVERSATION_LOADED,
                mapOf(
                    "message_count_bucket" to MessagesAnalyticsBuckets.count(messages.size),
                    "has_sent_message" to messages.any { it.isSent },
                ),
            )
        }
    }

    private suspend fun handleFailure(error: Throwable) {
        if (error is AccountNeedsReconnect || currentState().messages.isNotEmpty()) {
            setState { it.copy(isLoading = false) }
            return
        }
        analytics.reportShownError(
            screen = "conversation",
            reason = "load_failed:${error.shownErrorCause}",
            throwable = error,
        )
        val message = getString(Res.string.conversation_load_failed)
        setState { it.copy(isLoading = false, hasLoaded = true, errorMessage = message) }
    }
}
