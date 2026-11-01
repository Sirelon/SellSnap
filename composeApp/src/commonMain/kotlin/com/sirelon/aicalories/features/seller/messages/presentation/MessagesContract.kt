package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

interface MessagesContract {
    @Immutable
    data class ThreadRow(
        val threadId: Long,
        val advertId: Long,
        /** Null when the advert is not in the first adverts page or could not be loaded. */
        val title: String?,
        val imageUrl: String?,
        /** The advert's status. Null when it is not known yet (lookup pending or failed). */
        val status: AdvertStatus?,
        /** OLX answered 404 for the advert: it no longer exists. */
        val isListingDeleted: Boolean,
        val unreadCount: Int,
        val createdAt: String,
    )

    @Immutable
    data class State(
        /** The active account being shown. Null when none is connected. */
        val localIndex: Int? = null,
        val accountName: String? = null,
        val requiresOlxConnection: Boolean = false,
        val needsReconnect: Boolean = false,
        val isLoading: Boolean = false,
        val hasLoaded: Boolean = false,
        val threads: List<ThreadRow> = emptyList(),
        val isLoadingMore: Boolean = false,
        /** The last page OLX returned was full, so asking for the next one may find more. */
        val canLoadMore: Boolean = false,
        /** Set only while there is nothing else to show; see [MessagesViewModel]. */
        val errorMessage: String? = null,
    )

    sealed interface Event {
        /** The screen is in the foreground: load now and keep polling. */
        data object Shown : Event

        /** The screen left the foreground: stop polling. */
        data object Hidden : Event
        data object RefreshClicked : Event
        data object LoadMoreClicked : Event
        data class ThreadClicked(val row: ThreadRow) : Event
        data object ConnectOlxClicked : Event
        data object ReconnectClicked : Event
    }

    sealed interface Effect {
        /**
         * [title], [imageUrl], [status] and [isListingDeleted] are the row's, so the conversation
         * header has them before its own load.
         */
        data class OpenThread(
            val threadId: Long,
            val advertId: Long,
            val title: String?,
            val imageUrl: String?,
            val status: AdvertStatus?,
            val isListingDeleted: Boolean,
        ) : Effect
        data object ConnectOlx : Effect
        data object Reconnect : Effect
    }
}
