package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.reportShownError
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.domain.shownErrorCause
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.messages.data.activeAccount
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Effect
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationContract.State
import com.sirelon.sellsnap.features.seller.my_ads.data.AccountNeedsReconnect
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.conversation_load_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * One open conversation (read-only). [title], [imageUrl], [status] and [isListingDeleted] come
 * from the list row that opened it.
 * Loads the messages of [threadId] with the token of the account at [keyLocalIndex] - or, for a
 * key saved without one, the account that was active when it opened - whichever account is active
 * now. It shows the reconnect state while that account needs one, and closes itself if that
 * account is disconnected. As on the list, a failed load is shown (and reported) only while there
 * are no messages to show.
 */
class ConversationViewModel internal constructor(
    keyLocalIndex: Int?,
    private val threadId: Long,
    private val advertId: Long,
    private val title: String?,
    private val imageUrl: String?,
    private val status: AdvertStatus?,
    private val isListingDeleted: Boolean,
    private val repository: MessagesRepository,
    private val myAdvertsRepository: MyAdvertsRepository,
    private val accountRepository: SellerAccountRepository,
    private val analytics: Analytics,
) : BaseViewModel<State, Event, Effect>() {

    /** The thread's account. Null only while a key saved without one waits for an active account. */
    private var localIndex: Int? = keyLocalIndex
    private var isShown = false
    private var pollJob: Job? = null
    private var listingRequested = false
    private var accountSeen = false
    private var closed = false

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
        }
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
        setState { it.copy(isLoading = false, hasLoaded = true, messages = messages, errorMessage = null) }
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
