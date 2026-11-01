package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.reportShownError
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.domain.shownErrorCause
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.messages.data.activeAccount
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.Effect
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.State
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.ThreadRow
import com.sirelon.sellsnap.features.seller.my_ads.data.AccountNeedsReconnect
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.messages_load_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds

/** How often the thread list and an open conversation re-read OLX while on screen. */
internal val PollInterval = 30.seconds

private const val AdvertsPageSize = 50

/**
 * The Messages tab: the active account's buyer conversations (read-only).
 *
 * Results are tied to the account they were loaded for: a result that lands after the active
 * account changed is dropped. A failed load is shown (and reported) only while there is nothing
 * else to show; a refresh or poll that fails over an existing list leaves the list alone.
 */
class MessagesViewModel internal constructor(
    private val repository: MessagesRepository,
    private val myAdvertsRepository: MyAdvertsRepository,
    private val accountRepository: SellerAccountRepository,
    private val analytics: Analytics,
) : BaseViewModel<State, Event, Effect>() {

    private var isShown = false
    private var pollJob: Job? = null

    init {
        accountRepository
            .accountsRecordFlow
            .onEach(::syncAccount)
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

            Event.RefreshClicked -> currentState().localIndex?.let { index ->
                viewModelScope.launch { load(index, silent = false) }
            }

            is Event.ThreadClicked -> postEffect(Effect.OpenThread(event.row.threadId, event.row.advertId))
            Event.ConnectOlxClicked -> postEffect(Effect.ConnectOlx)
            Event.ReconnectClicked -> postEffect(Effect.Reconnect)
        }
    }

    private fun syncAccount(record: OlxAccountsRecord) {
        val account = record.activeAccount()
        val localIndex = account?.localIndex
        val needsReconnect = account?.state == OlxAccountState.NeedsReconnect
        val previous = currentState()
        // A reconnected account starts over too: its list was never loaded.
        val restart = previous.localIndex != localIndex || (previous.needsReconnect && !needsReconnect)

        setState {
            (if (restart) State() else it).copy(
                localIndex = localIndex,
                accountName = account?.profile?.name,
                requiresOlxConnection = account == null,
                needsReconnect = needsReconnect,
            )
        }
        if (restart) startPolling() else if (needsReconnect) pollJob?.cancel()
    }

    private fun startPolling() {
        pollJob?.cancel()
        val state = currentState()
        val localIndex = state.localIndex ?: return
        if (!isShown || state.needsReconnect) return

        pollJob = viewModelScope.launch {
            load(localIndex, silent = state.hasLoaded)
            while (true) {
                delay(PollInterval)
                load(localIndex, silent = true)
            }
        }
    }

    private suspend fun load(localIndex: Int, silent: Boolean) {
        if (!silent) setState { it.copy(isLoading = true, errorMessage = null) }

        val rows = try {
            val threads = repository.loadThreads(localIndex)
            // The listing join must not fail the list: without adverts a row just loses its title
            // and thumbnail. One adverts request per load, never one per row.
            val adverts = try {
                myAdvertsRepository.loadAdverts(localIndex, offset = 0, limit = AdvertsPageSize)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }.associateBy { it.id }
            threads.map { thread ->
                val advert = adverts[thread.advertId]
                ThreadRow(
                    threadId = thread.id,
                    advertId = thread.advertId,
                    title = advert?.title?.takeIf(String::isNotBlank),
                    imageUrl = advert?.primaryImageUrl,
                    unreadCount = thread.unreadCount,
                    createdAt = thread.createdAt,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleFailure(localIndex, error)
            return
        }

        if (currentState().localIndex != localIndex) return
        setState { it.copy(isLoading = false, hasLoaded = true, threads = rows, errorMessage = null) }
    }

    private suspend fun handleFailure(localIndex: Int, error: Throwable) {
        if (currentState().localIndex != localIndex) return
        // syncAccount already reflects NeedsReconnect from the account store; just stop spinning.
        if (error is AccountNeedsReconnect || currentState().threads.isNotEmpty()) {
            setState { it.copy(isLoading = false) }
            return
        }
        analytics.reportShownError(
            screen = "messages",
            reason = "load_failed:${error.shownErrorCause}",
            throwable = error,
        )
        val message = getString(Res.string.messages_load_failed)
        setState { it.copy(isLoading = false, hasLoaded = true, errorMessage = message) }
    }
}
