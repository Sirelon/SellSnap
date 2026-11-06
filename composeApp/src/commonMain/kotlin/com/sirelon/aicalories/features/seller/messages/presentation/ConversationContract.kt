package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.messages.domain.MessageSource
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.ReplySuggestion
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
         * The thread had unread messages when it was opened and has not been marked read yet:
         * "Mark as read" is on offer, and an accepted reply marks it. It is never set by opening,
         * reading or polling.
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
        /**
         * Ids of the received [messages] that match a scam rule (SIR-176, see `ScamSignals`). Each
         * one's bubble carries a warning. Never holds a sent message.
         */
        val flaggedMessageIds: Set<Long> = emptySet(),
        /** This platform can reach the model: false on web, which the AI proxy refuses (SIR-96). */
        val suggestionsSupported: Boolean = true,
        /** A suggestion request is in flight; placeholders stand where the suggestions will be. */
        val isSuggesting: Boolean = false,
        /** AI-suggested replies to the last buyer message, in the model's order. Empty when none are on offer. */
        val suggestions: List<ReplySuggestion> = emptyList(),
    ) {
        /** Send is enabled: there is something to send besides spaces and line breaks. */
        val canSend: Boolean get() = draft.isNotBlank()

        /** The newest message when the buyer wrote it, else null: the seller had the last word. */
        val lastBuyerMessage: OlxMessage? get() = messages.lastOrNull()?.takeIf { !it.isSent }

        /**
         * "Suggest replies" is on offer: the buyer wrote last, with no scam warning on it, nothing
         * of the seller's is on its way, the field is empty and the account can send.
         */
        val canSuggestReplies: Boolean
            get() {
                val buyerMessage = lastBuyerMessage ?: return false
                return suggestionsSupported && !needsReconnect && draft.isBlank() && pendingMessages.isEmpty() &&
                    !isSuggesting && suggestions.isEmpty() && buyerMessage.id !in flaggedMessageIds
            }

        /** The suggestions are on screen: they step aside while the seller has text in the field. */
        val showsSuggestions: Boolean get() = suggestions.isNotEmpty() && draft.isBlank() && !needsReconnect
    }

    /**
     * A reply on its way to OLX, sent, or refused. [localId] is this screen's own, not OLX's.
     * [source], [suggestionIndex] and [suggestionEdited] are for `message_sent` only, so a Retry
     * reports the reply as it was first sent.
     */
    @Immutable
    data class PendingMessage(
        val localId: Long,
        val text: String,
        val status: PendingStatus,
        val source: MessageSource = MessageSource.Typed,
        /** The suggestion row's position, for either suggestion source. */
        val suggestionIndex: Int? = null,
        /** For [MessageSource.EditedSuggestion]: the seller changed the text before sending. */
        val suggestionEdited: Boolean? = null,
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

        /**
         * The seller tapped "Mark as read". The command is also sent once a reply is accepted
         * while the thread is still unread, never on open, read or poll.
         */
        data object MarkAsReadClicked : Event

        /** "Copy" on a message's long-press menu: the screen has put the text on the clipboard; this only reports it. */
        data object MessageCopied : Event

        /** The listing header: the screen opens the advert; this only reports the tap. */
        data object ListingClicked : Event

        data class DraftChanged(val text: String) : Event

        /** [isTwoPane]: the conversation is beside the list. Only analytics reads it. */
        data class SendClicked(val isTwoPane: Boolean) : Event

        /** Retry on a failed reply: sends its text again. */
        data class RetrySendClicked(val localId: Long, val isTwoPane: Boolean) : Event

        /** "Suggest replies" (SIR-96). [isTwoPane] is for analytics only. */
        data class SuggestRepliesClicked(val isTwoPane: Boolean) : Event

        /**
         * A suggestion row, shown in full. One the model did not mark for review is sent; one it
         * marked goes into the field to be read and edited first.
         */
        data class SuggestionClicked(val index: Int, val isTwoPane: Boolean) : Event

        /** The suggestions' close icon. */
        data object SuggestionsDismissed : Event
    }

    sealed interface Effect {
        /** The thread's account was disconnected: this conversation has nowhere to load from. */
        data object Close : Effect
        data object Reconnect : Effect

        /** A failure to show the seller in a snackbar. */
        data class ShowMessage(val message: String) : Effect
    }
}
