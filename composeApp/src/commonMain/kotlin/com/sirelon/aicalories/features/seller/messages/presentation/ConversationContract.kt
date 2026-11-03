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
        /**
         * "Mark as read" is on offer: the thread had unread messages when it was opened and the
         * seller has not marked it yet. It is never set by opening, reading or polling.
         */
        val canMarkAsRead: Boolean = false,
        /** The mark-as-read command is in flight; the action is disabled meanwhile. */
        val isMarkingAsRead: Boolean = false,
        /** The reply the seller is typing. */
        val draft: String = "",
        /**
         * Replies written here that the loaded [messages] do not show yet, oldest first. They are
         * drawn after [messages]; a poll replaces [messages] and leaves these alone.
         */
        val pendingMessages: List<PendingMessage> = emptyList(),
        /** The seller's private lowest price for this advert; null when none was kept, and then nothing is shown. */
        val priceFloor: PriceFloor? = null,
    ) {
        /** Send is enabled: there is something to send besides spaces and line breaks. */
        val canSend: Boolean get() = draft.isNotBlank()
    }

    /**
     * What the seller said they would accept, kept on this device when the listing was published
     * here. For the seller's eyes only; whole units of [currencyCode]. [asking] is the advert's
     * current price, else the price it was published at; null when neither is known.
     */
    @Immutable
    data class PriceFloor(
        val floor: Long,
        val asking: Long?,
        val currencyCode: String,
    )

    /** A reply on its way to OLX, sent, or refused. [localId] is this screen's own, not OLX's. */
    @Immutable
    data class PendingMessage(
        val localId: Long,
        val text: String,
        val status: PendingStatus,
        /**
         * OLX's own words when it refused the message (`field: detail` of a validation error), or
         * null when it gave none: a network failure, a server error.
         */
        val olxRefusal: String? = null,
    )

    enum class PendingStatus {
        /** The request is in flight. */
        Sending,

        /** OLX accepted it; it stays until a load of the thread shows it. */
        Sent,

        /** OLX refused it or could not be reached: shown with Retry, its text kept. */
        Failed,
    }

    sealed interface Event {
        data object Shown : Event
        data object Hidden : Event
        data object RetryClicked : Event
        data object ReconnectClicked : Event

        /** "Reply on OLX" in the top bar: the screen opens OLX's answers page; this only reports the tap. */
        data object ReplyOnOlxClicked : Event

        /** The seller tapped "Mark as read" - the only thing that sends the command. */
        data object MarkAsReadClicked : Event

        /** The listing header: the screen opens the advert; this only reports the tap. */
        data object ListingClicked : Event

        data class DraftChanged(val text: String) : Event

        /** [isTwoPane]: the conversation is beside the list. Only analytics reads it. */
        data class SendClicked(val isTwoPane: Boolean) : Event

        /** Retry on a failed reply: sends its text again. */
        data class RetrySendClicked(val localId: Long, val isTwoPane: Boolean) : Event
    }

    sealed interface Effect {
        /** The thread's account was disconnected: this conversation has nowhere to load from. */
        data object Close : Effect
        data object Reconnect : Effect

        /** A failure to show the seller in a snackbar. */
        data class ShowMessage(val message: String) : Effect
    }
}
