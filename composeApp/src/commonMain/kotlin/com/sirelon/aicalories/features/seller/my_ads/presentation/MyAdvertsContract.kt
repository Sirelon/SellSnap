package com.sirelon.sellsnap.features.seller.my_ads.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem

interface MyAdvertsContract {
    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val requiresOlxConnection: Boolean = false,
        val adverts: List<MyAdvertItem> = emptyList(),
        val canLoadMore: Boolean = false,
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data object RefreshClicked : Event
        data object LoadMoreClicked : Event
        data object ConnectOlxClicked : Event
        data object CreateListingClicked : Event
        data class AdvertClicked(val advert: MyAdvertItem) : Event
    }

    sealed interface Effect {
        data class OpenUrl(val url: String) : Effect
        data class ShowMessage(val message: String) : Effect
        data object ConnectOlx : Effect
        data object CreateListing : Effect
    }
}
