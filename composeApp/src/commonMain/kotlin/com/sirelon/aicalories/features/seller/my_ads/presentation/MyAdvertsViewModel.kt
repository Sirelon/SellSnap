package com.sirelon.sellsnap.features.seller.my_ads.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.my_ads.data.AccountNeedsReconnect
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.AccountPage
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Effect
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Event
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.State
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.my_ads_account_fallback_name
import com.sirelon.sellsnap.generated.resources.my_ads_load_failed
import com.sirelon.sellsnap.generated.resources.my_ads_load_failed_account
import com.sirelon.sellsnap.generated.resources.my_ads_missing_url
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

private const val PageSize = 50

class MyAdvertsViewModel(
    private val repository: MyAdvertsRepository,
    private val accountRepository: SellerAccountRepository,
) : BaseViewModel<State, Event, Effect>() {

    init {
        // Every emission (initial + every add/reconnect/disconnect/switch) resyncs pages by
        // localIndex, never by list position - SIR-87 drops the switchEpoch staleness guard
        // entirely since every page now fetches with its own explicit token.
        accountRepository
            .accountsRecordFlow
            .onEach(::syncPages)
            .launchIn(viewModelScope)
    }

    override fun initialState(): State = State()

    override fun onEvent(event: Event) {
        when (event) {
            is Event.RefreshClicked -> fetchPage(event.localIndex)
            is Event.LoadMoreClicked -> loadMore(event.localIndex)
            is Event.PageSelected -> selectPage(event.localIndex)
            is Event.ReconnectClicked -> postEffect(Effect.Reconnect(event.localIndex))
            Event.ConnectOlxClicked -> postEffect(Effect.ConnectOlx)
            Event.CreateListingClicked -> postEffect(Effect.CreateListing)
            is Event.AdvertClicked -> openAdvert(event.advert)
        }
    }

    private fun syncPages(record: OlxAccountsRecord) {
        val countryCode = _currentOlxCountry.code
        val activeIndex = record.activeByCountry[countryCode]
        val countryAccounts = record.accounts
            .filter { it.countryCode == countryCode }
            .sortedBy { it.localIndex }

        val existingByIndex = currentState().pages.associateBy { it.localIndex }
        val pages = countryAccounts.map { account ->
            val needsReconnect = account.state == OlxAccountState.NeedsReconnect
            val existing = existingByIndex[account.localIndex]
            when {
                existing == null -> AccountPage(
                    localIndex = account.localIndex,
                    accountName = account.profile?.name,
                    avatarUrl = account.profile?.avatarUrl,
                    isActiveAccount = account.localIndex == activeIndex,
                    needsReconnect = needsReconnect,
                )
                // Reconnected: force a fresh load next time this page is selected.
                existing.needsReconnect && !needsReconnect -> existing.copy(
                    accountName = account.profile?.name,
                    avatarUrl = account.profile?.avatarUrl,
                    isActiveAccount = account.localIndex == activeIndex,
                    needsReconnect = false,
                    hasLoaded = false,
                )

                else -> existing.copy(
                    accountName = account.profile?.name,
                    avatarUrl = account.profile?.avatarUrl,
                    isActiveAccount = account.localIndex == activeIndex,
                    needsReconnect = needsReconnect,
                )
            }
        }

        // Keep the seller's current page unless it vanished (disconnected) or nothing was
        // selected yet - an external active-account switch (Profile) must not yank the page.
        val previouslySelected = currentState().selectedLocalIndex
        val selected = previouslySelected
            ?.takeIf { index -> pages.any { it.localIndex == index } }
            ?: activeIndex
            ?: pages.firstOrNull()?.localIndex

        setState {
            it.copy(
                pages = pages,
                selectedLocalIndex = selected,
                requiresOlxConnection = pages.isEmpty(),
            )
        }

        val selectedPage = pages.find { it.localIndex == selected }
        if (selectedPage != null && !selectedPage.hasLoaded && !selectedPage.isLoading) {
            fetchPage(selectedPage.localIndex)
        }
    }

    private fun selectPage(localIndex: Int) {
        val current = currentState()
        if (current.selectedLocalIndex == localIndex) return
        val page = current.pages.find { it.localIndex == localIndex } ?: return

        setState { it.copy(selectedLocalIndex = localIndex) }
        if (!page.hasLoaded && !page.isLoading) {
            fetchPage(localIndex)
        }
    }

    private fun fetchPage(localIndex: Int) {
        viewModelScope.launch {
            setState { it.updatePage(localIndex) { page -> page.copy(isLoading = true, isLoadingMore = false, errorMessage = null) } }
            runCatching { repository.loadAdverts(localIndex = localIndex, offset = 0, limit = PageSize) }
                .onSuccess { adverts ->
                    setState {
                        it.updatePage(localIndex) { page ->
                            page.copy(
                                isLoading = false,
                                adverts = adverts,
                                canLoadMore = adverts.size == PageSize,
                                errorMessage = null,
                                hasLoaded = true,
                            )
                        }
                    }
                }
                .onFailure { error -> handlePageFailure(localIndex, error, isLoadMore = false) }
        }
    }

    private fun loadMore(localIndex: Int) {
        val page = currentState().pages.find { it.localIndex == localIndex } ?: return
        if (page.isLoading || page.isLoadingMore || !page.canLoadMore) return

        viewModelScope.launch {
            setState { it.updatePage(localIndex) { p -> p.copy(isLoadingMore = true, errorMessage = null) } }
            runCatching { repository.loadAdverts(localIndex = localIndex, offset = page.adverts.size, limit = PageSize) }
                .onSuccess { adverts ->
                    setState {
                        it.updatePage(localIndex) { p ->
                            p.copy(
                                isLoadingMore = false,
                                adverts = p.adverts + adverts,
                                canLoadMore = adverts.size == PageSize,
                            )
                        }
                    }
                }
                .onFailure { error -> handlePageFailure(localIndex, error, isLoadMore = true) }
        }
    }

    private suspend fun handlePageFailure(localIndex: Int, error: Throwable, isLoadMore: Boolean) {
        if (error is AccountNeedsReconnect) {
            // syncPages already reflects NeedsReconnect from the account store; just stop
            // spinning - the reconnect card, not a generic error, is what the page should show.
            setState {
                it.updatePage(localIndex) { page ->
                    if (isLoadMore) page.copy(isLoadingMore = false) else page.copy(isLoading = false, hasLoaded = true)
                }
            }
            return
        }

        val message = if (error is OlxApiException) {
            val accountName = currentState().pages.find { it.localIndex == localIndex }
                ?.accountName
                ?.takeIf { it.isNotBlank() }
                ?: getString(Res.string.my_ads_account_fallback_name)
            getString(Res.string.my_ads_load_failed_account, accountName)
        } else {
            error.message ?: getString(Res.string.my_ads_load_failed)
        }
        setState {
            it.updatePage(localIndex) { page ->
                if (isLoadMore) {
                    page.copy(isLoadingMore = false, errorMessage = message)
                } else {
                    page.copy(isLoading = false, errorMessage = message, hasLoaded = true)
                }
            }
        }
        postEffect(Effect.ShowMessage(message))
    }

    private fun openAdvert(advert: MyAdvertItem) {
        if (advert.url.isBlank()) {
            viewModelScope.launch {
                postEffect(Effect.ShowMessage(getString(Res.string.my_ads_missing_url)))
            }
        } else {
            postEffect(Effect.OpenUrl(advert.url))
        }
    }
}

/** Mutates exactly one page by [localIndex], never by list position - a no-op if that page has
 * since vanished (account disconnected mid-flight), so a stale result for it is dropped. */
private fun State.updatePage(localIndex: Int, transform: (AccountPage) -> AccountPage): State =
    copy(pages = pages.map { if (it.localIndex == localIndex) transform(it) else it })
