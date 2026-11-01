package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem

interface ConversationContract {
    @Immutable
    data class State(
        /** The list row's title, so the header is not empty while [listing] loads. Null when the row had none. */
        val title: String? = null,
        val imageUrl: String? = null,
        /** The list row's status; [listing] supersedes it once loaded. Null when not known. */
        val status: AdvertStatus? = null,
        /** The advert no longer exists (OLX answered 404, on the list or here). */
        val isListingDeleted: Boolean = false,
        /** Null until loaded, and when the advert is gone; the header then shows only [title] and [imageUrl]. */
        val listing: MyAdvertItem? = null,
        /** Oldest first, as OLX returns them. */
        val messages: List<OlxMessage> = emptyList(),
        val isLoading: Boolean = false,
        val hasLoaded: Boolean = false,
        /** Set only while there are no messages to show; see [ConversationViewModel]. */
        val errorMessage: String? = null,
        /** The thread's account needs a reconnect: nothing loads until it has one. */
        val needsReconnect: Boolean = false,
        val accountName: String? = null,
    )

    sealed interface Event {
        data object Shown : Event
        data object Hidden : Event
        data object RetryClicked : Event
        data object ReconnectClicked : Event
    }

    sealed interface Effect {
        /** The thread's account was disconnected: this conversation has nowhere to load from. */
        data object Close : Effect
        data object Reconnect : Effect
    }
}
