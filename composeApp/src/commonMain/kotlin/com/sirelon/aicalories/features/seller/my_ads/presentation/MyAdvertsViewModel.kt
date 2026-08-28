package com.sirelon.sellsnap.features.seller.my_ads.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Effect
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Event
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.State
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.my_ads_load_failed
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
        // Reactive account-name (PRD U8): updates the header/empty-state immediately if the
        // active account changes while this screen is visible, no manual refresh needed.
        accountRepository.user
            .onEach { user -> setState { it.copy(accountName = user?.name) } }
            .launchIn(viewModelScope)

        // switchEpoch starts at 0 and is re-emitted (StateFlow replay) the instant this collector
        // starts, so this alone covers both the initial load and every subsequent account switch
        // (TRD A4) - no separate `refresh()` call needed here.
        accountRepository.switchEpoch
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    override fun initialState(): State = State()

    override fun onEvent(event: Event) {
        when (event) {
            Event.RefreshClicked -> refresh()
            Event.LoadMoreClicked -> loadMore()
            Event.ConnectOlxClicked -> postEffect(Effect.ConnectOlx)
            Event.CreateListingClicked -> postEffect(Effect.CreateListing)
            is Event.AdvertClicked -> openAdvert(event.advert)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            // TRD A4: snapshot the epoch before the account-scoped load. If it moves before the
            // load returns, the active account changed mid-flight - discard the result rather than
            // render it under the (now wrong) header. The switchEpoch collector in `init` already
            // fires a fresh `refresh()` for the new account, so nothing else needs to happen here.
            val epochAtStart = accountRepository.switchEpoch.value
            setState {
                it.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    errorMessage = null,
                )
            }
            runCatching {
                val session = accountRepository.currentSession()
                if (!session.isAuthorized) {
                    if (accountRepository.switchEpoch.value != epochAtStart) return@launch
                    setState {
                        it.copy(
                            isLoading = false,
                            requiresOlxConnection = true,
                            adverts = emptyList(),
                            canLoadMore = false,
                        )
                    }
                    return@launch
                }

                setState { it.copy(requiresOlxConnection = false) }
                repository.loadAdverts(offset = 0, limit = PageSize)
            }
                .onSuccess { adverts ->
                    if (accountRepository.switchEpoch.value != epochAtStart) return@onSuccess
                    setState {
                        it.copy(
                            isLoading = false,
                            requiresOlxConnection = false,
                            adverts = adverts,
                            canLoadMore = adverts.size == PageSize,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (accountRepository.switchEpoch.value != epochAtStart) return@onFailure
                    val message = if (error is OlxApiException) {
                        getString(Res.string.my_ads_load_failed)
                    } else {
                        error.message ?: getString(Res.string.my_ads_load_failed)
                    }
                    setState {
                        it.copy(
                            isLoading = false,
                            errorMessage = message,
                        )
                    }
                    postEffect(Effect.ShowMessage(message))
                }
        }
    }

    private fun loadMore() {
        val current = currentState()
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return

        viewModelScope.launch {
            // TRD A4: same staleness guard as `refresh()` - if the account switches while this
            // page is in flight, a `refresh()` for the new account is already triggered
            // reactively and will reset `isLoadingMore`, so discarding here is safe.
            val epochAtStart = accountRepository.switchEpoch.value
            setState { it.copy(isLoadingMore = true, errorMessage = null) }
            runCatching {
                repository.loadAdverts(offset = current.adverts.size, limit = PageSize)
            }
                .onSuccess { adverts ->
                    if (accountRepository.switchEpoch.value != epochAtStart) return@onSuccess
                    setState {
                        it.copy(
                            isLoadingMore = false,
                            adverts = it.adverts + adverts,
                            canLoadMore = adverts.size == PageSize,
                        )
                    }
                }
                .onFailure { error ->
                    if (accountRepository.switchEpoch.value != epochAtStart) return@onFailure
                    val message = if (error is OlxApiException) {
                        getString(Res.string.my_ads_load_failed)
                    } else {
                        error.message ?: getString(Res.string.my_ads_load_failed)
                    }
                    setState { it.copy(isLoadingMore = false, errorMessage = message) }
                    postEffect(Effect.ShowMessage(message))
                }
        }
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
