package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.lifecycle.viewModelScope
import com.aallam.openai.api.exception.OpenAIAPIException
import com.aallam.openai.api.exception.OpenAIException
import com.aallam.openai.api.exception.OpenAIServerException
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.analytics.reportShownError
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.shownErrorCause
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.messages.data.activeAccount
import com.sirelon.sellsnap.features.seller.messages.data.countryAccounts
import com.sirelon.sellsnap.features.seller.messages.domain.MessageSource
import com.sirelon.sellsnap.features.seller.messages.domain.MessagesAnalyticsBuckets
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.ReplyPriceGuard
import com.sirelon.sellsnap.features.seller.messages.domain.ReplySuggestion
import com.sirelon.sellsnap.features.seller.messages.domain.ScamSignals
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Effect
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.PendingMessage
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.PendingStatus
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.State
import com.sirelon.sellsnap.features.seller.my_ads.data.AccountNeedsReconnect
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeRecord
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.my_ads.model.AdvertListingSnapshot
import com.sirelon.sellsnap.features.seller.openai.MAX_REPLY_SUGGESTIONS
import com.sirelon.sellsnap.features.seller.openai.OpenAIClient
import com.sirelon.sellsnap.features.seller.openai.REPLY_SUGGESTIONS_MODEL_ID
import com.sirelon.sellsnap.features.seller.openai.REPLY_SUGGESTIONS_PROMPT_VERSION
import com.sirelon.sellsnap.features.seller.openai.replySuggestionsInput
import com.sirelon.sellsnap.features.seller.openai.replySuggestionsInstructions
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.conversation_load_failed
import com.sirelon.sellsnap.generated.resources.generic_error_retry
import com.sirelon.sellsnap.platform.PlatformTargets
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The whole wait for reply suggestions, from the tap: the listing read, then the model. No retry beyond it. */
private val SuggestionsBudget = 15.seconds

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
 *
 * [State.priceFloor] is the seller's private lowest price, read once from [outcomeStore] by advert
 * id, so it shows for every account and is absent when none was kept. The asking price beside it
 * is the advert's own once loaded, else the price it was published at.
 *
 * A received message that matches a scam rule is flagged in [State.flaggedMessageIds] and its
 * bubble shows a warning (SIR-176). The first load with a flagged message logs
 * `messages_scam_warning_shown` once; `message_sent` says whether a warning was on screen.
 *
 * "Suggest replies" (SIR-96) asks [openAIClient] for up to three replies to the last buyer
 * message, on a tap only, never on its own. The prompt carries the listing from the same advert
 * read the header uses ([listingSnapshot]), the newest messages, the account's market language
 * and the price floor; [ReplyPriceGuard] drops what offers less than the floor. A chip whose
 * whole text is on screen and that the model did not mark for review is sent at once; any other
 * goes into the field for the seller to read first. The answer is kept for its buyer message, so
 * tapping again shows it without a new call, until a newer message arrives. A failure shows
 * nothing: the button comes back. Nothing about the suggestions is stored on the device.
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
    private val outcomeStore: AdvertOutcomeStore,
    private val accountRepository: SellerAccountRepository,
    private val analytics: Analytics,
    private val openAIClient: OpenAIClient,
    /** Web has no App Check, so the AI proxy refuses it: no suggestions there. */
    private val suggestionsSupported: Boolean = !PlatformTargets.isWeb(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : BaseViewModel<State, Event, Effect>() {

    private val openedAt = timeSource.markNow()
    private var loadedLogged = false
    private var scamWarningLogged = false

    /** The thread's account. Null only while a key saved without one waits for an active account. */
    private var localIndex: Int? = keyLocalIndex
    private var isShown = false
    private var pollJob: Job? = null
    private var listingRequested = false
    private var accountSeen = false
    private var closed = false
    private var nextPendingId = 0L
    private var outcome: AdvertOutcomeRecord? = null

    /** The thread account's market: the suggestions' language and currency (rule 11). */
    private var country: OlxCountry = _currentOlxCountry

    /** The advert read once per conversation; null when it is deleted or could not be read. */
    private val listingSnapshot = CompletableDeferred<AdvertListingSnapshot?>()

    /** The last suggestions shown, for the buyer message they answer. */
    private var keptSuggestions: KeptSuggestions? = null

    /** The suggestion the seller put into the field, until the field is cleared or sent. */
    private var insertedSuggestion: InsertedSuggestion? = null

    private class KeptSuggestions(val buyerMessageId: Long, val suggestions: List<ReplySuggestion>)

    private class InsertedSuggestion(val index: Int, val text: String)

    /** Every message id a load has returned, so each load can tell which sent messages are new. */
    private val seenMessageIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            outcome = runCatching { outcomeStore.outcomeFor(advertId) }.getOrNull()
            updatePriceFloor()
        }
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
        suggestionsSupported = suggestionsSupported,
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
            is Event.DraftChanged -> {
                // An emptied field is no longer the suggestion that was put into it.
                if (event.text.isBlank()) insertedSuggestion = null
                setState { it.copy(draft = event.text) }
            }
            is Event.SendClicked -> send(event.isTwoPane)
            is Event.RetrySendClicked -> retrySend(event.localId, event.isTwoPane)
            is Event.SuggestRepliesClicked -> suggestReplies(event.isTwoPane)
            is Event.SuggestionClicked -> onSuggestionClicked(event.index, event.isTruncated, event.isTwoPane)
            Event.SuggestionsDismissed -> dismissSuggestions()
        }
    }

    private fun send(isTwoPane: Boolean) {
        val text = currentState().draft.trim()
        if (text.isEmpty()) return
        val inserted = insertedSuggestion
        val queued = enqueue(
            text = text,
            source = if (inserted != null) MessageSource.EditedSuggestion else MessageSource.Typed,
            suggestionIndex = inserted?.index,
            suggestionEdited = inserted?.let { text != it.text.trim() },
            isTwoPane = isTwoPane,
        )
        if (queued) insertedSuggestion = null
    }

    /** Clears the field, adds the pending bubble and sends it; false when nothing can be sent now. */
    private fun enqueue(
        text: String,
        source: MessageSource,
        suggestionIndex: Int?,
        suggestionEdited: Boolean?,
        isTwoPane: Boolean,
    ): Boolean {
        val index = localIndex ?: return false
        // The input is not on screen while the account needs a reconnect; a send would only fail.
        if (currentState().needsReconnect) return false
        val pending = PendingMessage(
            localId = nextPendingId++,
            text = text,
            status = PendingStatus.Sending,
            source = source,
            suggestionIndex = suggestionIndex,
            suggestionEdited = suggestionEdited,
        )
        setState { it.copy(draft = "", pendingMessages = it.pendingMessages + pending) }
        deliver(index, pending, isRetry = false, isTwoPane = isTwoPane)
        return true
    }

    private fun suggestReplies(isTwoPane: Boolean) {
        val state = currentState()
        if (!state.canSuggestReplies) return
        val buyerMessage = state.lastBuyerMessage ?: return
        keptSuggestions?.takeIf { it.buyerMessageId == buyerMessage.id }?.let { kept ->
            setState { it.copy(suggestions = kept.suggestions) }
            return
        }
        setState { it.copy(isSuggesting = true) }
        val tappedAt = timeSource.markNow()
        viewModelScope.launch {
            // Read in parallel with the messages at open, so this is normally done already.
            val listing = withTimeoutOrNull(SuggestionsBudget) { listingSnapshot.await() }
            val floor = currentState().priceFloor
            analytics.logEvent(
                AnalyticsEvents.SUGGESTIONS_REQUESTED,
                mapOf(
                    "layout" to layoutCode(isTwoPane),
                    "has_listing_text" to
                        (listing != null && (listing.description.isNotBlank() || listing.attributes.isNotEmpty())),
                    "has_price_floor" to (floor != null),
                ),
            )
            val result = requestSuggestions(listing, floor, budget = SuggestionsBudget - tappedAt.elapsedNow())
            val latency = MessagesAnalyticsBuckets.latencyMs(tappedAt.elapsedNow().inWholeMilliseconds)
            // A newer message arrived meanwhile: these replies answer one the seller no longer sees last.
            if (currentState().lastBuyerMessage?.id != buyerMessage.id) {
                setState { it.copy(isSuggesting = false) }
                return@launch
            }
            if (result == null) {
                failSuggestions("timeout", IllegalStateException("Reply suggestions timed out"), latency)
                return@launch
            }
            val suggestions = result.getOrElse { error ->
                failSuggestions(error.suggestionFailureReason(), error, latency)
                return@launch
            }
            val guarded = ReplyPriceGuard.apply(suggestions, floor?.floor, buyerText = buyerMessage.text)
            val shown = guarded.kept.take(MAX_REPLY_SUGGESTIONS)
            if (shown.isEmpty()) {
                failSuggestions(
                    reason = "empty",
                    error = IllegalStateException("Reply suggestions: no usable reply"),
                    latency = latency,
                    droppedBelowFloor = guarded.dropped,
                )
                return@launch
            }
            keptSuggestions = KeptSuggestions(buyerMessage.id, shown)
            setState { it.copy(isSuggesting = false, suggestions = shown) }
            analytics.logEvent(
                AnalyticsEvents.SUGGESTIONS_SHOWN,
                mapOf(
                    "count" to shown.size,
                    "latency_ms_bucket" to latency,
                    "dropped_below_floor" to guarded.dropped,
                    "needs_review_count" to shown.count { it.needsReview },
                    "prompt_version" to REPLY_SUGGESTIONS_PROMPT_VERSION,
                    "model" to REPLY_SUGGESTIONS_MODEL_ID,
                ),
            )
        }
    }

    /**
     * One model call within [budget]: null when it ran out, else the replies or the failure.
     * Without the listing ([listing] null) the row title and the conversation are all it gets.
     */
    private suspend fun requestSuggestions(
        listing: AdvertListingSnapshot?,
        floor: ConversationContract.PriceFloor?,
        budget: Duration,
    ): Result<List<ReplySuggestion>>? {
        val item = listing?.item
        val currencyCode = listOfNotNull(floor?.currencyCode, item?.currencyCode, outcome?.currency)
            .firstOrNull { it.isNotBlank() }
            ?: country.currencyCode
        val instructions = replySuggestionsInstructions(
            country = country,
            currencyCode = currencyCode,
            askingPrice = floor?.asking ?: item?.priceValue ?: outcome?.publishedPrice,
            priceFloor = floor?.floor,
        )
        val input = replySuggestionsInput(
            title = item?.title?.takeIf { it.isNotBlank() } ?: title.orEmpty(),
            description = listing?.description,
            attributes = listing?.attributes.orEmpty(),
            messages = currentState().messages,
        )
        return withTimeoutOrNull(budget) {
            try {
                Result.success(openAIClient.suggestReplies(instructions = instructions, input = input))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }

    /** Nothing is shown to the seller: the button comes back. Logged and recorded, never `error_shown`. */
    private fun failSuggestions(reason: String, error: Throwable, latency: String, droppedBelowFloor: Int? = null) {
        setState { it.copy(isSuggesting = false) }
        analytics.logEvent(
            AnalyticsEvents.SUGGESTIONS_FAILED,
            listOfNotNull(
                "reason" to reason,
                "latency_ms_bucket" to latency,
                droppedBelowFloor?.let { "dropped_below_floor" to it },
            ).toMap(),
        )
        analytics.recordException(throwable = error, message = "suggestions_failed: $reason")
    }

    private fun Throwable.suggestionFailureReason(): String = when (this) {
        is OpenAITimeoutException -> "timeout"
        is OpenAIAPIException -> "openai_$statusCode"
        // A 5xx: the status is on the Ktor exception it wraps.
        is OpenAIServerException -> (cause as? ResponseException)?.response?.status?.value
            ?.let { "openai_$it" } ?: "network"
        is OpenAIException -> "network"
        else -> "parse"
    }

    /**
     * A chip whose whole text is on screen and that the model did not mark for review is sent;
     * any other goes into the field, so the seller never sends what they have not read in full.
     */
    private fun onSuggestionClicked(index: Int, isTruncated: Boolean, isTwoPane: Boolean) {
        val state = currentState()
        if (!state.showsSuggestions) return
        val suggestion = state.suggestions.getOrNull(index) ?: return
        if (!isTruncated && !suggestion.needsReview) {
            val queued = enqueue(
                text = suggestion.text,
                source = MessageSource.Suggestion,
                suggestionIndex = index,
                suggestionEdited = null,
                isTwoPane = isTwoPane,
            )
            if (queued) {
                keptSuggestions = null
                setState { it.copy(suggestions = emptyList()) }
            }
            return
        }
        insertedSuggestion = InsertedSuggestion(index, suggestion.text)
        setState { it.copy(draft = suggestion.text) }
        analytics.logEvent(
            AnalyticsEvents.SUGGESTION_INSERTED,
            mapOf(
                "suggestion_index" to index,
                "reason" to if (suggestion.needsReview) "needs_review" else "truncated",
            ),
        )
    }

    /** Hidden, not forgotten: "Suggest replies" shows the same ones again without a new call. */
    private fun dismissSuggestions() {
        val count = currentState().suggestions.size
        if (count == 0) return
        setState { it.copy(suggestions = emptyList()) }
        analytics.logEvent(AnalyticsEvents.SUGGESTIONS_DISMISSED, mapOf("count" to count))
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
                logMessageSent(pending, result = "failed", isRetry = isRetry, isTwoPane = isTwoPane)
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
            logMessageSent(pending, result = "success", isRetry = isRetry, isTwoPane = isTwoPane)
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

    private fun logMessageSent(pending: PendingMessage, result: String, isRetry: Boolean, isTwoPane: Boolean) {
        analytics.logEvent(
            AnalyticsEvents.MESSAGE_SENT,
            listOfNotNull(
                "source" to pending.source.code,
                "result" to result,
                "is_retry" to isRetry,
                "layout" to layoutCode(isTwoPane),
                "account_count" to accountRepository.accountsRecordFlow.value.countryAccounts().size,
                "had_scam_warning" to currentState().flaggedMessageIds.isNotEmpty(),
                pending.suggestionIndex?.let { "suggestion_index" to it },
                pending.suggestionEdited?.let { "suggestion_edited" to it },
            ).toMap(),
        )
    }

    private fun layoutCode(isTwoPane: Boolean) = if (isTwoPane) "two_pane" else "single_pane"

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
        country = OlxCountry.fromCode(account.countryCode) ?: country
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
     * row passed in. Either way [listingSnapshot] completes, so suggestions never wait on it.
     */
    private fun loadListingOnce(index: Int) {
        if (listingRequested) return
        listingRequested = true
        if (isListingDeleted) {
            listingSnapshot.complete(null)
            return
        }
        viewModelScope.launch {
            val snapshot = try {
                myAdvertsRepository.loadAdvertSnapshotOrNull(index, advertId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                listingSnapshot.complete(null)
                return@launch
            }
            listingSnapshot.complete(snapshot)
            val listing = snapshot?.item
            setState { it.copy(listing = listing, isListingDeleted = listing == null) }
            updatePriceFloor()
        }
    }

    private fun updatePriceFloor() {
        val record = outcome ?: return
        val floor = record.minPrice?.takeIf { it > 0 } ?: return
        val listing = currentState().listing
        val sameCurrency = listing != null &&
            (record.currency.isBlank() || listing.currencyCode.equals(record.currency, ignoreCase = true))
        val asking = (if (sameCurrency) listing?.priceValue else null) ?: record.publishedPrice
        setState {
            it.copy(priceFloor = ConversationContract.PriceFloor(floor, asking, record.currency))
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
        val signalsByMessage = scamSignalsOf(messages)
        // Kept suggestions answer one buyer message; once anything newer is last, they are stale.
        val lastBuyerMessageId = messages.lastOrNull()?.takeIf { !it.isSent }?.id
        if (keptSuggestions?.buyerMessageId != lastBuyerMessageId) keptSuggestions = null
        val suggestionsKept = keptSuggestions != null
        setState {
            it.copy(
                isLoading = false,
                hasLoaded = true,
                messages = messages,
                errorMessage = null,
                pendingMessages = pending,
                flaggedMessageIds = signalsByMessage.keys,
                suggestions = if (suggestionsKept) it.suggestions else emptyList(),
            )
        }
        logScamWarningOnce(signalsByMessage)
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

    /** The signals of each received message that matches a scam rule, by message id. The seller's own are never checked. */
    private fun scamSignalsOf(messages: List<OlxMessage>): Map<Long, List<String>> =
        messages
            .filter { !it.isSent }
            .associate { it.id to ScamSignals.detect(it.text) }
            .filterValues { it.isNotEmpty() }

    /** Once per open conversation, at the first load that shows a warning; see `messages_scam_warning_shown`. */
    private fun logScamWarningOnce(signalsByMessage: Map<Long, List<String>>) {
        if (scamWarningLogged || signalsByMessage.isEmpty()) return
        scamWarningLogged = true
        analytics.logEvent(
            AnalyticsEvents.MESSAGES_SCAM_WARNING_SHOWN,
            mapOf(
                "signals" to signalsByMessage.values.flatten().distinct().sorted().joinToString(","),
                "flagged_count_bucket" to MessagesAnalyticsBuckets.count(signalsByMessage.size),
            ),
        )
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
