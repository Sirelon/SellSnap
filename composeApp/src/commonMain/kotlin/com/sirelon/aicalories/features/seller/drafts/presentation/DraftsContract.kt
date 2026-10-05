package com.sirelon.sellsnap.features.seller.drafts.presentation

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.drafts.Draft

interface DraftsContract {

    @Immutable
    data class DraftsState(
        /**
         * The current OLX country's drafts, most recently updated first. Null until the first read
         * from the database, so the screen shows nothing rather than an empty state while the push
         * transition runs.
         */
        val drafts: List<Draft>? = null,
        val currency: OlxCurrency = OlxCurrency.Default,
    )

    sealed interface DraftsEvent {
        data class Open(val draft: Draft) : DraftsEvent

        data class Remove(val draft: Draft) : DraftsEvent

        /** Puts back the draft removed last. */
        data object UndoRemove : DraftsEvent
    }

    sealed interface DraftsEffect {
        data class OpenPreview(val listing: AdvertisementWithAttributes) : DraftsEffect

        data object DraftRemoved : DraftsEffect
    }
}
