package com.sirelon.sellsnap.features.seller.my_ads.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Effect
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Event
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.State
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.my_ads_load_failed
import com.sirelon.sellsnap.generated.resources.my_ads_missing_url
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

private const val PageSize = 50

class MyAdvertsViewModel(
    private val repository: MyAdvertsRepository,
    private val accountRepository: SellerAccountRepository,
) : BaseViewModel<State, Event, Effect>() {

    init {
        refresh()
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
                    val message = error.message ?: getString(Res.string.my_ads_load_failed)
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
            setState { it.copy(isLoadingMore = true, errorMessage = null) }
            runCatching {
                repository.loadAdverts(offset = current.adverts.size, limit = PageSize)
            }
                .onSuccess { adverts ->
                    setState {
                        it.copy(
                            isLoadingMore = false,
                            adverts = it.adverts + adverts,
                            canLoadMore = adverts.size == PageSize,
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: getString(Res.string.my_ads_load_failed)
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
