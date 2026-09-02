package com.sirelon.sellsnap.features.seller.my_ads.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem

interface MyAdvertsContract {
    /** One connected account's page in the pager (SIR-87). Loads, pages, errors and retries
     * independently of every other page. */
    @Immutable
    data class AccountPage(
        val localIndex: Int,
        /** Falls back to [com.sirelon.sellsnap.generated.resources.Res.string.my_ads_account_fallback_name]
         * when null/blank, same as the pre-pager single-account screen. */
        val accountName: String?,
        val avatarUrl: String?,
        /** Drives the "Active" badge on this page's tab - viewing a non-active page never changes
         * which account is active for the app (that stays a Profile/Publish action). */
        val isActiveAccount: Boolean,
        val needsReconnect: Boolean,
        /** False until `fetchPage` starts a request - a freshly-created page has nothing in
         * flight yet, so this must not default to true or the lazy first load's `!isLoading`
         * guard is permanently unsatisfiable. */
        val isLoading: Boolean = false,
        val isLoadingMore: Boolean = false,
        val adverts: List<MyAdvertItem> = emptyList(),
        val canLoadMore: Boolean = false,
        val errorMessage: String? = null,
        /** Gates the lazy first load on page selection - only the initially selected page loads
         * on screen entry, never all connected accounts at once. */
        val hasLoaded: Boolean = false,
    )

    @Immutable
    data class State(
        val pages: List<AccountPage> = emptyList(),
        val selectedLocalIndex: Int? = null,
        /** No accounts at all for the current country - distinct from any single page's
         * [AccountPage.needsReconnect]. */
        val requiresOlxConnection: Boolean = false,
    )

    sealed interface Event {
        data class RefreshClicked(val localIndex: Int) : Event
        data class LoadMoreClicked(val localIndex: Int) : Event
        data class PageSelected(val localIndex: Int) : Event
        data class ReconnectClicked(val localIndex: Int) : Event
        data object ConnectOlxClicked : Event
        data object CreateListingClicked : Event
        data class AdvertClicked(val advert: MyAdvertItem) : Event
    }

    sealed interface Effect {
        data class OpenUrl(val url: String) : Effect
        data class ShowMessage(val message: String) : Effect
        data object ConnectOlx : Effect
        data object CreateListing : Effect
        /** Reuses Profile's existing reconnect route rather than a second auth entry point. */
        data class Reconnect(val localIndex: Int) : Effect
    }
}
