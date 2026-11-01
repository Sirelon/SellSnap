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
import com.sirelon.sellsnap.features.seller.messages.domain.OlxThread
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.Effect
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.Event
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.State
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.ThreadRow
import com.sirelon.sellsnap.features.seller.my_ads.data.AccountNeedsReconnect
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
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
 *
 * Each row's listing comes from the first adverts page, joined by advert id. That page is read on
 * the first load, a manual refresh and a load more - never on a poll, which reuses it. An advert
 * that is not on the page is read on its own, once per id: a 404 means it was deleted (kept for
 * the life of the account), another failure leaves the row unknown until the next manual refresh.
 * A lookup never blocks the list and never runs on a poll's schedule of its own.
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
    private var lookupJob: Job? = null

    /** Pages of threads fetched for the current account, so the next one is [loadedPages]. */
    private var loadedPages = 0

    /** The adverts page last read, by id. Null while none has been read successfully. */
    private var advertsById: Map<Long, MyAdvertItem>? = null

    /** An adverts page was requested for this account, whatever came back: polls do not ask again. */
    private var advertsRequested = false

    /** Advert ids absent from [advertsById] and what `GET adverts/{id}` said about each. */
    private val lookups = HashMap<Long, Lookup>()

    /** Ids whose lookup failed since the last manual refresh; polls do not retry them. */
    private val failedLookups = HashSet<Long>()

    private sealed interface Lookup {
        data class Found(val advert: MyAdvertItem) : Lookup
        data object Deleted : Lookup
    }

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
                viewModelScope.launch { load(index, page = 0, silent = false, refresh = true) }
            }

            Event.LoadMoreClicked -> loadMore()

            is Event.ThreadClicked -> postEffect(
                Effect.OpenThread(
                    threadId = event.row.threadId,
                    advertId = event.row.advertId,
                    title = event.row.title,
                    imageUrl = event.row.imageUrl,
                    status = event.row.status,
                    isListingDeleted = event.row.isListingDeleted,
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
            lookupJob?.cancel()
            advertsById = null
            advertsRequested = false
            lookups.clear()
            failedLookups.clear()
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
     *
     * The adverts page is read only on the first load, on a [refresh] and with a later page; a
     * poll joins against the page already read.
     */
    private suspend fun load(localIndex: Int, page: Int, silent: Boolean, refresh: Boolean = false) {
        val isMore = page > 0
        if (isMore) {
            setState { it.copy(isLoadingMore = true) }
        } else if (!silent) {
            setState { it.copy(isLoading = true, errorMessage = null) }
        }

        val fetchAdverts = refresh || isMore || !advertsRequested
        val loaded = try {
            val threadsPage = repository.loadThreads(localIndex, page)
            // The listing join must not fail the list: without adverts a row just loses its title
            // and thumbnail. One adverts request per load, never one per row.
            val adverts = if (fetchAdverts) {
                try {
                    myAdvertsRepository.loadAdverts(localIndex, offset = 0, limit = AdvertsPageSize)
                        .associateBy { it.id }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            Triple(threadsPage.threads.map(::threadRow), threadsPage.canLoadMore, adverts)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleFailure(localIndex, error, isMore)
            return
        }

        if (currentState().localIndex != localIndex) return
        val (rows, canLoadMore, adverts) = loaded
        if (fetchAdverts) {
            advertsRequested = true
            // A failed read keeps the page already held rather than blanking every row.
            if (adverts != null) advertsById = adverts
        }
        if (refresh) {
            lookups.values.removeAll { it is Lookup.Found }
            failedLookups.clear()
        }
        loadedPages = maxOf(loadedPages, page + 1)
        setState { state ->
            val known = state.threads.mapTo(HashSet()) { it.threadId }
            val fresh = rows.mapTo(HashSet()) { it.threadId }
            state.copy(
                isLoading = state.isLoading && isMore,
                isLoadingMore = state.isLoadingMore && !isMore,
                hasLoaded = true,
                threads = (
                    if (isMore) {
                        state.threads + rows.filter { it.threadId !in known }
                    } else {
                        rows + state.threads.filter { it.threadId !in fresh }
                    }
                    ).map { it.withListing() },
                // A refresh only knows about the first page; once more are loaded, the last one
                // fetched decides.
                canLoadMore = if (isMore || loadedPages == 1) canLoadMore else state.canLoadMore,
                errorMessage = null,
            )
        }
        repository.publishUnread(currentState().threads.sumOf { it.unreadCount })
        lookUpMissingAdverts(localIndex)
    }

    private fun threadRow(thread: OlxThread) = ThreadRow(
        threadId = thread.id,
        advertId = thread.advertId,
        title = null,
        imageUrl = null,
        status = null,
        isListingDeleted = false,
        unreadCount = thread.unreadCount,
        createdAt = thread.createdAt,
    )

    /** The adverts page wins over a lookup: it is the fresher of the two. */
    private fun ThreadRow.withListing(): ThreadRow {
        val advert = advertsById?.get(advertId) ?: (lookups[advertId] as? Lookup.Found)?.advert
        return when {
            advert != null -> copy(
                title = advert.title.takeIf(String::isNotBlank),
                imageUrl = advert.primaryImageUrl,
                status = advert.status,
                isListingDeleted = false,
            )

            lookups[advertId] is Lookup.Deleted -> copy(
                title = null,
                imageUrl = null,
                status = null,
                isListingDeleted = true,
            )

            else -> copy(title = null, imageUrl = null, status = null, isListingDeleted = false)
        }
    }

    /**
     * Advert ids shown in the list that are neither on the adverts page nor looked up yet. Empty
     * while no adverts page has been read: with nothing to compare against, every row would look
     * missing.
     */
    private fun missingAdvertIds(): List<Long> {
        val page = advertsById ?: return emptyList()
        return currentState().threads
            .map { it.advertId }
            .distinct()
            .filter { it !in page && it !in lookups && it !in failedLookups }
    }

    /**
     * One `GET adverts/{id}` per missing advert, one after another, patching the rows as each
     * answer lands. A single coroutine drains the list; a load that finds it running adds nothing,
     * because the loop re-reads what is missing before each request.
     */
    private fun lookUpMissingAdverts(localIndex: Int) {
        if (lookupJob?.isActive == true || missingAdvertIds().isEmpty()) return
        lookupJob = viewModelScope.launch {
            while (true) {
                val advertId = missingAdvertIds().firstOrNull() ?: break
                val advert = try {
                    myAdvertsRepository.loadAdvertOrNull(localIndex, advertId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (currentState().localIndex != localIndex) return@launch
                    failedLookups += advertId
                    continue
                }
                if (currentState().localIndex != localIndex) return@launch
                lookups[advertId] = if (advert == null) Lookup.Deleted else Lookup.Found(advert)
                setState { state -> state.copy(threads = state.threads.map { it.withListing() }) }
            }
        }
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
