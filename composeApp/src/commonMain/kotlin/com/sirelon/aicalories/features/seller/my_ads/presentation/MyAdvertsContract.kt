package com.sirelon.sellsnap.features.seller.my_ads.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatistics
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
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

    /**
     * The open advert sheet (SIR-101/103/105). Everything a seller can do with one listing lives
     * here rather than in a new screen: the state OLX has it in, how long it has left, how it is
     * performing, and the actions OLX will actually accept.
     */
    @Immutable
    data class AdvertSheet(
        val localIndex: Int,
        val advert: MyAdvertItem,
        /** Only what OLX will accept for this status - see
         * [com.sirelon.sellsnap.features.seller.my_ads.domain.availableActions]. Empty means the
         * state is explained instead. */
        val actions: List<AdvertAction>,
        /** Live listing in a market where OLX rejects `extend`. Shown as a note, never a button. */
        val extendUnavailableHere: Boolean,
        /** OLX's own explanation, fetched for listings OLX is reviewing or has rejected. Preferred
         * over this app's copy whenever OLX has something to say - it is the only account of the
         * state that is authoritative. */
        val olxReason: String? = null,
        val statistics: OlxAdvertStatistics? = null,
        val isLoadingStatistics: Boolean = false,
        val statisticsFailed: Boolean = false,
        /** Why the last action failed, shown inside the sheet. A snackbar cannot be seen behind
         * the sheet's own scrim, and the failure belongs to the action the seller just pressed -
         * which is in here, along with the button to try again. */
        val errorMessage: String? = null,
        /** Non-null while an action is in flight. Every action is disabled meanwhile, so a
         * double tap cannot send a command twice - the second one would fail against OLX for a
         * status the advert has already left. */
        val pendingAction: AdvertAction? = null,
    )

    /** A destructive or state-changing action waiting on an explicit yes (SIR-101). */
    @Immutable
    data class ActionConfirm(
        val localIndex: Int,
        val advert: MyAdvertItem,
        val action: AdvertAction,
    )

    /**
     * OLX's "did it sell?" (SIR-102). Not an added feature: `deactivate` will not be accepted
     * without an answer, so this fires on the only path that can take a listing down.
     *
     * [thenDelete] marks the prompt as a step inside deleting a live listing rather than a plain
     * take-down - an active advert has to be deactivated before OLX accepts a delete, so the
     * question is unavoidable there too.
     */
    @Immutable
    data class SoldPrompt(
        val localIndex: Int,
        val advert: MyAdvertItem,
        val thenDelete: Boolean,
        /** True once the seller answered "it sold" and is being asked for the price. */
        val askingPrice: Boolean = false,
        val isSubmitting: Boolean = false,
    )

    /** The edit sheet (SIR-104). Text and price only; see [MyAdvertsViewModel.editSnapshot]. */
    @Immutable
    data class AdvertEdit(
        val localIndex: Int,
        val advert: MyAdvertItem,
        val isLoading: Boolean = true,
        val loadFailed: Boolean = false,
        val isSaving: Boolean = false,
        /** Why the save failed. Inline for the same reason as [AdvertSheet.errorMessage], and more
         * so here: the edit sheet sits over the actions sheet, so a snackbar is behind two scrims. */
        val errorMessage: String? = null,
        /** Seeded from `GET adverts/{id}`, which is the only place the description exists - the
         * list call does not return it. */
        val title: String = "",
        val description: String = "",
        val priceValue: Long? = null,
    )

    @Immutable
    data class State(
        val pages: List<AccountPage> = emptyList(),
        val selectedLocalIndex: Int? = null,
        /** No accounts at all for the current country - distinct from any single page's
         * [AccountPage.needsReconnect]. */
        val requiresOlxConnection: Boolean = false,
        val advertSheet: AdvertSheet? = null,
        val actionConfirm: ActionConfirm? = null,
        val soldPrompt: SoldPrompt? = null,
        val advertEdit: AdvertEdit? = null,
    )

    sealed interface Event {
        data class RefreshClicked(val localIndex: Int) : Event
        data class LoadMoreClicked(val localIndex: Int) : Event
        data class PageSelected(val localIndex: Int) : Event
        data class ReconnectClicked(val localIndex: Int) : Event
        data object ConnectOlxClicked : Event
        data object CreateListingClicked : Event
        data class AdvertClicked(val localIndex: Int, val advert: MyAdvertItem) : Event

        data object AdvertSheetDismissed : Event
        data object OpenOnOlxClicked : Event

        /** Routes through [ActionConfirm] or [SoldPrompt] first where one is warranted. */
        data class ActionClicked(val action: AdvertAction) : Event
        data object ActionConfirmed : Event
        data object ActionDismissed : Event

        data class SoldAnswered(val isSold: Boolean) : Event
        /** Null price means the seller skipped the optional field, which stays a completed answer. */
        data class SoldPriceSubmitted(val price: Long?) : Event
        data object SoldPromptDismissed : Event

        data class EditSubmitted(val title: String, val description: String, val price: Long?) : Event
        data object EditDismissed : Event
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
