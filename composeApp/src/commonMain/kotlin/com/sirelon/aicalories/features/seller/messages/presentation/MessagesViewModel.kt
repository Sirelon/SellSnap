package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.analytics.reportShownError
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.shownErrorCause
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.messages.data.activeAccount
import com.sirelon.sellsnap.features.seller.messages.data.countryAccounts
import com.sirelon.sellsnap.features.seller.messages.domain.MessagesAnalyticsBuckets
import com.sirelon.sellsnap.features.seller.messages.domain.OlxThread
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesContract.AccountPage
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
 * The Messages tab: the buyer conversations of every connected account of the current country,
 * one page per account (read-only), as in My Ads.
 *
 * Only the selected page polls; another page loads when it is first selected, and selecting a page
 * that has loaded refreshes it silently. Results are tied to the account they were loaded for: a
 * result that lands after that account was disconnected or reconnected is dropped. A failed load
 * is shown (and reported) only while the page has nothing else to show; a refresh or poll that
 * fails over an existing list leaves the list alone.
 *
 * Each row's listing comes from the account's first adverts page, joined by advert id. That page
 * is read on the first load, a manual refresh and a load more - never on a poll, which reuses it.
 * An advert that is not on the page is read on its own, once per id: a 404 means it was deleted
 * (kept for the life of the page), another failure leaves the row unknown until the next manual
 * refresh. A lookup never blocks the list and never runs on a poll's schedule of its own.
 */
class MessagesViewModel internal constructor(
    private val repository: MessagesRepository,
    private val myAdvertsRepository: MyAdvertsRepository,
    private val accountRepository: SellerAccountRepository,
    private val analytics: Analytics,
) : BaseViewModel<State, Event, Effect>() {

    private var isShown = false
    private var pollJob: Job? = null
    private var openedLogged = false

    /** What each page knows beyond its rows, by localIndex. Replaced when the account reconnects. */
    private val caches = HashMap<Int, AccountCache>()

    private class AccountCache {
        /** Pages of threads fetched, so the next one is [loadedPages]. */
        var loadedPages = 0

        /** The adverts page last read, by id. Null while none has been read successfully. */
        var advertsById: Map<Long, MyAdvertItem>? = null

        /** An adverts page was requested, whatever came back: polls do not ask again. */
        var advertsRequested = false

        /** Advert ids absent from [advertsById] and what `GET adverts/{id}` said about each. */
        val lookups = HashMap<Long, Lookup>()

        /** Ids whose lookup failed since the last manual refresh; polls do not retry them. */
        val failedLookups = HashSet<Long>()

        /** The first page was read. Starts the one `messages_list_loaded` of this account's page. */
        var firstPageRead = false
        var buyerSideCount = 0
        var listLoadedLogged = false

        var lookupJob: Job? = null
        var loadMoreJob: Job? = null

        fun cancel() {
            lookupJob?.cancel()
            loadMoreJob?.cancel()
        }
    }

    private sealed interface Lookup {
        data class Found(val advert: MyAdvertItem) : Lookup
        data object Deleted : Lookup
    }

    init {
        accountRepository
            .accountsRecordFlow
            .onEach(::syncPages)
            .launchIn(viewModelScope)
    }

    override fun initialState(): State = State()

    override fun onEvent(event: Event) {
        when (event) {
            Event.Shown -> {
                isShown = true
                if (!openedLogged) {
                    openedLogged = true
                    analytics.logEvent(
                        AnalyticsEvents.MESSAGES_OPENED,
                        mapOf("badge_unread_bucket" to MessagesAnalyticsBuckets.count(repository.unreadCount.value)),
                    )
                }
                startPolling()
            }

            Event.Hidden -> {
                isShown = false
                pollJob?.cancel()
            }

            Event.RefreshClicked -> currentState().selectedPage?.takeIf { !it.needsReconnect }?.let { page ->
                viewModelScope.launch { load(page.localIndex, page = 0, silent = false, refresh = true) }
            }

            Event.LoadMoreClicked -> loadMore()

            is Event.PageSelected -> selectPage(event.localIndex)

            is Event.ThreadClicked -> {
                logConversationOpened(event)
                postEffect(
                    Effect.OpenThread(
                        localIndex = event.localIndex,
                        threadId = event.row.threadId,
                        advertId = event.row.advertId,
                        title = event.row.title,
                        imageUrl = event.row.imageUrl,
                        status = event.row.status,
                        isListingDeleted = event.row.isListingDeleted,
                        hadUnread = event.row.unreadCount > 0,
                    ),
                )
            }
            Event.ConnectOlxClicked -> postEffect(Effect.ConnectOlx)
            Event.ReconnectClicked -> postEffect(Effect.Reconnect)
        }
    }

    private fun logConversationOpened(event: Event.ThreadClicked) {
        val activeIndex = accountRepository.accountsRecordFlow.value.activeAccount()?.localIndex
        analytics.logEvent(
            AnalyticsEvents.MESSAGES_CONVERSATION_OPENED,
            mapOf(
                "layout" to if (event.isTwoPane) "two_pane" else "single_pane",
                "account_count" to currentState().pages.size,
                "is_active_account" to (event.localIndex == activeIndex),
                "had_unread" to (event.row.unreadCount > 0),
                "listing_state" to MessagesAnalyticsBuckets.listingState(event.row.status, event.row.isListingDeleted),
            ),
        )
    }

    private fun syncPages(record: OlxAccountsRecord) {
        val activeIndex = record.activeByCountry[_currentOlxCountry.code]
        val previous = currentState()
        val existingByIndex = previous.pages.associateBy { it.localIndex }

        val pages = record.countryAccounts().map { account ->
            val needsReconnect = account.state == OlxAccountState.NeedsReconnect
            val existing = existingByIndex[account.localIndex]
            // A reconnected account starts over: its list was never loaded with the new session.
            val restart = existing == null || (existing.needsReconnect && !needsReconnect)
            if (restart) caches.remove(account.localIndex)?.cancel()
            val page = existing?.takeUnless { restart }
                ?: AccountPage(localIndex = account.localIndex, accountName = null, isActiveAccount = false, needsReconnect = false)
            page.copy(
                accountName = account.profile?.name,
                isActiveAccount = account.localIndex == activeIndex,
                needsReconnect = needsReconnect,
            )
        }
        val listed = pages.mapTo(HashSet()) { it.localIndex }
        caches.keys.filter { it !in listed }.forEach { caches.remove(it)?.cancel() }

        // Keep the seller's page unless it vanished; an active-account switch elsewhere must not
        // move it.
        val selected = previous.selectedLocalIndex?.takeIf { it in listed }
            ?: activeIndex?.takeIf { it in listed }
            ?: pages.firstOrNull()?.localIndex

        setState { it.copy(pages = pages, selectedLocalIndex = selected, requiresOlxConnection = pages.isEmpty()) }

        // Another page on screen, or the one on screen lost or regained its session.
        val before = previous.selectedPage
        val after = currentState().selectedPage
        if (before?.localIndex != after?.localIndex || before?.needsReconnect != after?.needsReconnect) startPolling()
    }

    private fun selectPage(localIndex: Int) {
        val current = currentState()
        if (current.selectedLocalIndex == localIndex || current.pages.none { it.localIndex == localIndex }) return
        setState { it.copy(selectedLocalIndex = localIndex) }
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        val page = currentState().selectedPage ?: return
        if (!isShown || page.needsReconnect) return

        val localIndex = page.localIndex
        pollJob = viewModelScope.launch {
            load(localIndex, page = 0, silent = page.hasLoaded)
            while (true) {
                delay(PollInterval)
                load(localIndex, page = 0, silent = true)
            }
        }
    }

    private fun loadMore() {
        val page = currentState().selectedPage ?: return
        if (!page.canLoadMore || page.isLoadingMore || page.isLoading) return
        val cache = cacheFor(page.localIndex)
        cache.loadMoreJob = viewModelScope.launch {
            load(page.localIndex, page = cache.loadedPages, silent = true)
        }
    }

    private fun cacheFor(localIndex: Int): AccountCache = caches.getOrPut(localIndex) { AccountCache() }

    /**
     * Loads [page] of an account's threads and merges it into that page by thread id. Page 0 (a
     * refresh or a poll) replaces the first page and keeps whatever later pages are already
     * loaded; a later page is appended.
     *
     * The adverts page is read only on the first load, on a [refresh] and with a later page; a
     * poll joins against the page already read.
     */
    private suspend fun load(localIndex: Int, page: Int, silent: Boolean, refresh: Boolean = false) {
        val cache = cacheFor(localIndex)
        val isMore = page > 0
        if (isMore) {
            setState { it.updatePage(localIndex) { p -> p.copy(isLoadingMore = true) } }
        } else if (!silent) {
            setState { it.updatePage(localIndex) { p -> p.copy(isLoading = true, errorMessage = null) } }
        }

        val fetchAdverts = refresh || isMore || !cache.advertsRequested
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
            LoadResult(
                rows = threadsPage.threads.map(::threadRow),
                canLoadMore = threadsPage.canLoadMore,
                adverts = adverts,
                buyerSideCount = threadsPage.buyerSideCount,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            handleFailure(localIndex, cache, error, isMore, page)
            return
        }

        if (caches[localIndex] !== cache) return
        val (rows, canLoadMore, adverts, buyerSideCount) = loaded
        if (fetchAdverts) {
            cache.advertsRequested = true
            // A failed read keeps the page already held rather than blanking every row.
            if (adverts != null) cache.advertsById = adverts
        }
        if (refresh) {
            cache.lookups.values.removeAll { it is Lookup.Found }
            cache.failedLookups.clear()
        }
        cache.loadedPages = maxOf(cache.loadedPages, page + 1)
        if (isMore) {
            val known = currentState().pages.find { it.localIndex == localIndex }?.threads.orEmpty()
                .mapTo(HashSet()) { it.threadId }
            logLoadMore(page, if (rows.any { it.threadId !in known }) "threads" else "empty")
        } else if (!cache.firstPageRead) {
            cache.firstPageRead = true
            cache.buyerSideCount = buyerSideCount
        }
        setState { state ->
            state.updatePage(localIndex) { p ->
                val known = p.threads.mapTo(HashSet()) { it.threadId }
                val fresh = rows.mapTo(HashSet()) { it.threadId }
                p.copy(
                    isLoading = p.isLoading && isMore,
                    isLoadingMore = p.isLoadingMore && !isMore,
                    hasLoaded = true,
                    threads = (
                        if (isMore) {
                            p.threads + rows.filter { it.threadId !in known }
                        } else {
                            rows + p.threads.filter { it.threadId !in fresh }
                        }
                        ).map { it.withListing(cache) },
                    // A refresh only knows about the first page; once more are loaded, the last
                    // one fetched decides.
                    canLoadMore = if (isMore || cache.loadedPages == 1) canLoadMore else p.canLoadMore,
                    errorMessage = null,
                )
            }
        }
        currentState().pages.find { it.localIndex == localIndex }?.let { p ->
            repository.publishUnread(localIndex, p.threads.sumOf { it.unreadCount })
        }
        lookUpMissingAdverts(localIndex, cache)
        logListLoadedOnce(localIndex, cache)
    }

    private class LoadResult(
        val rows: List<ThreadRow>,
        val canLoadMore: Boolean,
        val adverts: Map<Long, MyAdvertItem>?,
        val buyerSideCount: Int,
    ) {
        operator fun component1() = rows
        operator fun component2() = canLoadMore
        operator fun component3() = adverts
        operator fun component4() = buyerSideCount
    }

    private fun logLoadMore(page: Int, result: String) {
        analytics.logEvent(AnalyticsEvents.MESSAGES_LOAD_MORE, mapOf("page" to page, "result" to result))
    }

    /**
     * The one `messages_list_loaded` per account page: once the first page was read and every
     * advert on it has an answer, so the deleted and inactive counts are complete. Called after
     * each load and when the lookups finish; whichever comes second logs.
     */
    private fun logListLoadedOnce(localIndex: Int, cache: AccountCache) {
        if (cache.listLoadedLogged || !cache.firstPageRead) return
        if (missingAdvertIds(localIndex, cache).isNotEmpty()) return
        val state = currentState()
        val rows = state.pages.find { it.localIndex == localIndex }?.threads ?: return
        cache.listLoadedLogged = true
        analytics.logEvent(
            AnalyticsEvents.MESSAGES_LIST_LOADED,
            mapOf(
                "account_index" to localIndex,
                "account_count" to state.pages.size,
                "thread_count_bucket" to MessagesAnalyticsBuckets.count(rows.size),
                "unread_count_bucket" to MessagesAnalyticsBuckets.count(rows.sumOf { it.unreadCount }),
                "buyer_side_count_bucket" to MessagesAnalyticsBuckets.count(cache.buyerSideCount),
                "inactive_listing_count_bucket" to MessagesAnalyticsBuckets.count(
                    rows.count { it.status != null && it.status != AdvertStatus.Active },
                ),
                "deleted_listing_count_bucket" to MessagesAnalyticsBuckets.count(rows.count { it.isListingDeleted }),
            ),
        )
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
    private fun ThreadRow.withListing(cache: AccountCache): ThreadRow {
        val advert = cache.advertsById?.get(advertId) ?: (cache.lookups[advertId] as? Lookup.Found)?.advert
        return when {
            advert != null -> copy(
                title = advert.title.takeIf(String::isNotBlank),
                imageUrl = advert.primaryImageUrl,
                status = advert.status,
                isListingDeleted = false,
            )

            cache.lookups[advertId] is Lookup.Deleted -> copy(
                title = null,
                imageUrl = null,
                status = null,
                isListingDeleted = true,
            )

            else -> copy(title = null, imageUrl = null, status = null, isListingDeleted = false)
        }
    }

    /**
     * Advert ids shown on the page that are neither on its adverts page nor looked up yet. Empty
     * while no adverts page has been read: with nothing to compare against, every row would look
     * missing.
     */
    private fun missingAdvertIds(localIndex: Int, cache: AccountCache): List<Long> {
        val advertsPage = cache.advertsById ?: return emptyList()
        val page = currentState().pages.find { it.localIndex == localIndex } ?: return emptyList()
        return page.threads
            .map { it.advertId }
            .distinct()
            .filter { it !in advertsPage && it !in cache.lookups && it !in cache.failedLookups }
    }

    /**
     * One `GET adverts/{id}` per missing advert, one after another, patching the rows as each
     * answer lands. A single coroutine per account drains the list; a load that finds it running
     * adds nothing, because the loop re-reads what is missing before each request.
     */
    private fun lookUpMissingAdverts(localIndex: Int, cache: AccountCache) {
        if (cache.lookupJob?.isActive == true || missingAdvertIds(localIndex, cache).isEmpty()) return
        cache.lookupJob = viewModelScope.launch {
            while (true) {
                val advertId = missingAdvertIds(localIndex, cache).firstOrNull() ?: break
                val advert = try {
                    myAdvertsRepository.loadAdvertOrNull(localIndex, advertId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (caches[localIndex] !== cache) return@launch
                    cache.failedLookups += advertId
                    continue
                }
                if (caches[localIndex] !== cache) return@launch
                cache.lookups[advertId] = if (advert == null) Lookup.Deleted else Lookup.Found(advert)
                setState { state ->
                    state.updatePage(localIndex) { p -> p.copy(threads = p.threads.map { it.withListing(cache) }) }
                }
            }
            logListLoadedOnce(localIndex, cache)
        }
    }

    private suspend fun handleFailure(
        localIndex: Int,
        cache: AccountCache,
        error: Throwable,
        isMore: Boolean,
        pageNumber: Int,
    ) {
        if (caches[localIndex] !== cache) return
        if (isMore && error !is AccountNeedsReconnect) logLoadMore(pageNumber, "failed")
        val page = currentState().pages.find { it.localIndex == localIndex } ?: return
        // syncPages already reflects NeedsReconnect from the account store; just stop spinning.
        // A failed "load more" leaves the button to press again.
        if (error is AccountNeedsReconnect || page.threads.isNotEmpty()) {
            setState {
                it.updatePage(localIndex) { p ->
                    if (isMore) p.copy(isLoadingMore = false) else p.copy(isLoading = false)
                }
            }
            return
        }
        analytics.reportShownError(
            screen = "messages",
            reason = "load_failed:${error.shownErrorCause}",
            throwable = error,
        )
        val message = getString(Res.string.messages_load_failed)
        if (caches[localIndex] !== cache) return
        setState {
            it.updatePage(localIndex) { p -> p.copy(isLoading = false, hasLoaded = true, errorMessage = message) }
        }
    }
}

private fun State.updatePage(localIndex: Int, transform: (AccountPage) -> AccountPage): State =
    copy(pages = pages.map { if (it.localIndex == localIndex) transform(it) else it })
