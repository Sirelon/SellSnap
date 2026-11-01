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
    private var loadMoreJob: Job? = null

    /** Pages of threads fetched for the current account, so the next one is [loadedPages]. */
    private var loadedPages = 0

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
                viewModelScope.launch { load(index, page = 0, silent = false) }
            }

            Event.LoadMoreClicked -> loadMore()

            is Event.ThreadClicked -> postEffect(
                Effect.OpenThread(
                    threadId = event.row.threadId,
                    advertId = event.row.advertId,
                    title = event.row.title,
                    imageUrl = event.row.imageUrl,
                ),
            )
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
        if (restart) {
            loadedPages = 0
            loadMoreJob?.cancel()
        }

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
            load(localIndex, page = 0, silent = state.hasLoaded)
            while (true) {
                delay(PollInterval)
                load(localIndex, page = 0, silent = true)
            }
        }
    }

    private fun loadMore() {
        val state = currentState()
        val localIndex = state.localIndex ?: return
        if (!state.canLoadMore || state.isLoadingMore || state.isLoading) return
        loadMoreJob = viewModelScope.launch { load(localIndex, page = loadedPages, silent = true) }
    }

    /**
     * Loads [page] of the threads and merges it into the list by thread id. Page 0 (a refresh or
     * a poll) replaces the first page and keeps whatever later pages are already loaded; a later
     * page is appended.
     */
    private suspend fun load(localIndex: Int, page: Int, silent: Boolean) {
        val isMore = page > 0
        if (isMore) {
            setState { it.copy(isLoadingMore = true) }
        } else if (!silent) {
            setState { it.copy(isLoading = true, errorMessage = null) }
        }

        val loaded = try {
            val threadsPage = repository.loadThreads(localIndex, page)
            // The listing join must not fail the list: without adverts a row just loses its title
            // and thumbnail. One adverts request per load, never one per row.
            val adverts = try {
                myAdvertsRepository.loadAdverts(localIndex, offset = 0, limit = AdvertsPageSize)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }.associateBy { it.id }
            threadsPage.threads.map { thread ->
                val advert = adverts[thread.advertId]
                ThreadRow(
                    threadId = thread.id,
                    advertId = thread.advertId,
                    title = advert?.title?.takeIf(String::isNotBlank),
                    imageUrl = advert?.primaryImageUrl,
                    unreadCount = thread.unreadCount,
                    createdAt = thread.createdAt,
                )
            } to threadsPage.canLoadMore
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleFailure(localIndex, error, isMore)
            return
        }

        if (currentState().localIndex != localIndex) return
        val (rows, canLoadMore) = loaded
        loadedPages = maxOf(loadedPages, page + 1)
        setState { state ->
            val known = state.threads.mapTo(HashSet()) { it.threadId }
            val fresh = rows.mapTo(HashSet()) { it.threadId }
            state.copy(
                isLoading = state.isLoading && isMore,
                isLoadingMore = state.isLoadingMore && !isMore,
                hasLoaded = true,
                threads = if (isMore) {
                    state.threads + rows.filter { it.threadId !in known }
                } else {
                    rows + state.threads.filter { it.threadId !in fresh }
                },
                // A refresh only knows about the first page; once more are loaded, the last one
                // fetched decides.
                canLoadMore = if (isMore || loadedPages == 1) canLoadMore else state.canLoadMore,
                errorMessage = null,
            )
        }
        repository.publishUnread(currentState().threads.sumOf { it.unreadCount })
    }

    private suspend fun handleFailure(localIndex: Int, error: Throwable, isMore: Boolean) {
        if (currentState().localIndex != localIndex) return
        // syncAccount already reflects NeedsReconnect from the account store; just stop spinning.
        // A failed "load more" leaves the button to press again.
        if (error is AccountNeedsReconnect || currentState().threads.isNotEmpty()) {
            setState {
                if (isMore) it.copy(isLoadingMore = false) else it.copy(isLoading = false)
            }
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
