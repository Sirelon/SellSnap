package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem

interface ConversationContract {
    @Immutable
    data class State(
        /** Null until loaded, and when the advert is gone; the header then shows a fallback title. */
        val listing: MyAdvertItem? = null,
        /** Oldest first, as OLX returns them. */
        val messages: List<OlxMessage> = emptyList(),
        val isLoading: Boolean = false,
        val hasLoaded: Boolean = false,
        /** Set only while there are no messages to show; see [ConversationViewModel]. */
        val errorMessage: String? = null,
    )

    sealed interface Event {
        data object Shown : Event
        data object Hidden : Event
        data object RetryClicked : Event
    }

    sealed interface Effect {
        /** The active account changed or lost its session: this conversation has nowhere to load from. */
        data object Close : Effect
    }
}
