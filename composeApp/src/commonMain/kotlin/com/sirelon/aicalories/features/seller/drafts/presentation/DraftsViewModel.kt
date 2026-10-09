package com.sirelon.sellsnap.features.seller.drafts.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.drafts.Draft
import com.sirelon.sellsnap.features.seller.drafts.DraftsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The Drafts screen: every draft of the current OLX country, removable with an undo. The price has
 * no currency of its own and reopening a draft publishes to the current country, so drafts of
 * another country stay out of the list (and in the table, for when the seller switches back).
 */
class DraftsViewModel(
    private val draftsRepository: DraftsRepository,
    private val countryStore: OlxCountryStore,
    private val adFlowTimerStore: AdFlowTimerStore,
    private val analytics: Analytics,
) : BaseViewModel<DraftsContract.DraftsState, DraftsContract.DraftsEvent, DraftsContract.DraftsEffect>() {

    private var removedDraft: Draft? = null

    init {
        combine(draftsRepository.drafts(), countryStore.currentFlow) { all, country ->
            all.filter { it.countryCode == country.code } to OlxCurrency.fallbackFor(country)
        }
            .onEach { (drafts, currency) ->
                setState { it.copy(drafts = drafts, currency = currency) }
            }
            .launchIn(viewModelScope)
    }

    override fun initialState() = DraftsContract.DraftsState()

    override fun onEvent(event: DraftsContract.DraftsEvent) {
        when (event) {
            is DraftsContract.DraftsEvent.Open -> {
                analytics.logEvent(AnalyticsEvents.DRAFT_OPENED, mapOf("source" to "drafts"))
                // The success screen's "total time" reads AdFlowTimerStore. Restart it so a reopened
                // draft counts from the reopen, not from whenever an unrelated flow started.
                adFlowTimerStore.clear()
                adFlowTimerStore.markFlowStartedIfNeeded()
                postEffect(DraftsContract.DraftsEffect.OpenPreview(listing = event.draft.listing))
            }

            is DraftsContract.DraftsEvent.Remove -> {
                removedDraft = event.draft
                analytics.logEvent(AnalyticsEvents.DRAFT_REMOVED)
                viewModelScope.launch {
                    draftsRepository.delete(event.draft.id)
                    postEffect(DraftsContract.DraftsEffect.DraftRemoved)
                }
            }

            DraftsContract.DraftsEvent.UndoRemove -> {
                val draft = removedDraft ?: return
                removedDraft = null
                viewModelScope.launch { draftsRepository.upsert(draft) }
            }
        }
    }
}
