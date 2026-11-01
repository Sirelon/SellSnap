package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.reportShownError
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * One open conversation (read-only). Loads the messages of [threadId] for the account that was
 * active when it opened and closes itself if that account changes or loses its session. As on the
 * list, a failed load is shown (and reported) only while there are no messages to show.
 */
class ConversationViewModel internal constructor(
    private val threadId: Long,
    private val advertId: Long,
    private val repository: MessagesRepository,
    private val myAdvertsRepository: MyAdvertsRepository,
    private val accountRepository: SellerAccountRepository,
    private val analytics: Analytics,
) : BaseViewModel<State, Event, Effect>() {

    private var localIndex: Int? = null
    private var isShown = false
    private var pollJob: Job? = null
    private var listingRequested = false

    init {
        accountRepository
            .accountsRecordFlow
            .map { record -> record.activeAccount()?.takeIf { it.state != OlxAccountState.NeedsReconnect }?.localIndex }
            .distinctUntilChanged()
            .onEach(::onAccount)
            .launchIn(viewModelScope)
    }

    override fun initialState(): State = State()

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
        }
    }

    private fun onAccount(activeIndex: Int?) {
        val opened = localIndex
        if (opened != null && opened != activeIndex) {
            pollJob?.cancel()
            postEffect(Effect.Close)
            return
        }
        localIndex = activeIndex
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        val index = localIndex ?: return
        if (!isShown) return

        loadListingOnce(index)
        pollJob = viewModelScope.launch {
            loadMessages(index, silent = currentState().hasLoaded)
            while (true) {
                delay(PollInterval)
                loadMessages(index, silent = true)
            }
        }
    }

    /** One advert request per open conversation. A failure leaves the header on its fallback title. */
    private fun loadListingOnce(index: Int) {
        if (listingRequested) return
        listingRequested = true
        viewModelScope.launch {
            val listing = try {
                myAdvertsRepository.loadAdvert(index, advertId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            setState { it.copy(listing = listing) }
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
