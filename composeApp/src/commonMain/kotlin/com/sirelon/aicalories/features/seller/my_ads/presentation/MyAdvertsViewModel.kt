package com.sirelon.sellsnap.features.seller.my_ads.presentation

import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.common.presentation.BaseViewModel
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.data.AdvertCommand
import com.sirelon.sellsnap.features.seller.auth.data.AdvertEditSnapshot
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.my_ads.data.AccountNeedsReconnect
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertDeactivatedNotDeleted
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertLifecycleRepository
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAnalyticsBuckets
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertState
import com.sirelon.sellsnap.features.seller.my_ads.domain.state
import com.sirelon.sellsnap.features.seller.my_ads.domain.availableActions
import com.sirelon.sellsnap.features.seller.my_ads.domain.isLive
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.AccountPage
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.ActionConfirm
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.AdvertEdit
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.AdvertSheet
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Effect
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.Event
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.SoldPrompt
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsContract.State
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.advert_action_done_deactivate
import com.sirelon.sellsnap.generated.resources.advert_action_done_delete
import com.sirelon.sellsnap.generated.resources.advert_action_done_edit
import com.sirelon.sellsnap.generated.resources.advert_action_done_edit_moderated
import com.sirelon.sellsnap.generated.resources.advert_action_done_extend
import com.sirelon.sellsnap.generated.resources.advert_action_done_reactivate
import com.sirelon.sellsnap.generated.resources.advert_action_failed
import com.sirelon.sellsnap.generated.resources.advert_action_failed_generic
import com.sirelon.sellsnap.generated.resources.advert_action_needs_reconnect
import com.sirelon.sellsnap.generated.resources.advert_delete_partial
import com.sirelon.sellsnap.generated.resources.advert_edit_load_failed
import com.sirelon.sellsnap.generated.resources.my_ads_account_fallback_name
import com.sirelon.sellsnap.generated.resources.my_ads_load_failed
import com.sirelon.sellsnap.generated.resources.my_ads_load_failed_account
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

private const val PageSize = 50

class MyAdvertsViewModel internal constructor(
    private val repository: MyAdvertsRepository,
    private val accountRepository: SellerAccountRepository,
    private val lifecycleRepository: AdvertLifecycleRepository,
    private val outcomeStore: AdvertOutcomeStore,
    private val analytics: Analytics,
) : BaseViewModel<State, Event, Effect>() {

    /**
     * The advert as OLX last returned it, held for the open edit sheet only. An edit has to send
     * the whole payload back (`PUT` has no patch semantics), so this is the exact JSON the edit
     * is rebuilt from - keeping it out of [State] keeps an internal data-layer type out of the
     * public contract, and it is not something the UI renders.
     */
    private var editSnapshot: AdvertEditSnapshot? = null

    /**
     * Actions currently in flight, by advert id. Held here rather than only in
     * [AdvertSheet.pendingAction] because the sheet can be swiped away and reopened while a
     * command is still running: a fresh sheet would come back with every button enabled, and a
     * second `finish` or `DELETE` against an advert that has already left that status is exactly
     * the phantom OLX error this milestone is trying to stop producing.
     */
    private val inFlightActions = mutableMapOf<Long, AdvertAction>()

    /**
     * What a landed command implies about a row, until OLX catches up.
     *
     * OLX answers a command with 204 and then keeps reporting the old status on `GET adverts` for
     * a moment, so every action refetches the list AND records what it expects. A reload applies
     * [expected] to the row for as long as the server is still reporting [actedFrom] - the value
     * we know is stale because we just changed it. The moment the server reports anything else it
     * has caught up, the entry is dropped, and the server wins even if it resolved the advert to
     * a different status than the one anticipated.
     */
    private class PendingStatus(val actedFrom: AdvertStatus, val expected: AdvertStatus)

    private val pendingStatuses = mutableMapOf<Long, PendingStatus>()

    init {
        // Every emission (initial + every add/reconnect/disconnect/switch) resyncs pages by
        // localIndex, never by list position - SIR-87 drops the switchEpoch staleness guard
        // entirely since every page now fetches with its own explicit token.
        accountRepository
            .accountsRecordFlow
            .onEach(::syncPages)
            .launchIn(viewModelScope)
    }

    override fun initialState(): State = State()

    override fun onEvent(event: Event) {
        when (event) {
            is Event.RefreshClicked -> fetchPage(event.localIndex)
            is Event.LoadMoreClicked -> loadMore(event.localIndex)
            is Event.PageSelected -> selectPage(event.localIndex)
            is Event.ReconnectClicked -> postEffect(Effect.Reconnect(event.localIndex))
            Event.ConnectOlxClicked -> postEffect(Effect.ConnectOlx)
            Event.CreateListingClicked -> postEffect(Effect.CreateListing)
            is Event.AdvertClicked -> openAdvertSheet(event.localIndex, event.advert)

            Event.AdvertSheetDismissed -> setState { it.copy(advertSheet = null) }
            Event.OpenOnOlxClicked -> openOnOlx()

            is Event.ActionClicked -> startAction(event.action)
            Event.ActionConfirmed -> runConfirmedAction()
            Event.ActionDismissed -> setState { it.copy(actionConfirm = null) }

            is Event.SoldAnswered -> answerSold(event.isSold)
            is Event.SoldPriceSubmitted -> submitSoldPrice(event.price)
            Event.SoldPromptDismissed -> setState { it.copy(soldPrompt = null) }

            is Event.EditSubmitted -> submitEdit(event.title, event.description, event.price)
            Event.EditDismissed -> {
                editSnapshot = null
                setState { it.copy(advertEdit = null) }
            }
        }
    }

    private fun syncPages(record: OlxAccountsRecord) {
        val countryCode = _currentOlxCountry.code
        val activeIndex = record.activeByCountry[countryCode]
        val countryAccounts = record.accounts
            .filter { it.countryCode == countryCode }
            .sortedBy { it.localIndex }

        val existingByIndex = currentState().pages.associateBy { it.localIndex }
        val pages = countryAccounts.map { account ->
            val needsReconnect = account.state == OlxAccountState.NeedsReconnect
            val existing = existingByIndex[account.localIndex]
            when {
                existing == null -> AccountPage(
                    localIndex = account.localIndex,
                    accountName = account.profile?.name,
                    avatarUrl = account.profile?.avatarUrl,
                    isActiveAccount = account.localIndex == activeIndex,
                    needsReconnect = needsReconnect,
                )
                // Reconnected: force a fresh load next time this page is selected.
                existing.needsReconnect && !needsReconnect -> existing.copy(
                    accountName = account.profile?.name,
                    avatarUrl = account.profile?.avatarUrl,
                    isActiveAccount = account.localIndex == activeIndex,
                    needsReconnect = false,
                    hasLoaded = false,
                )

                else -> existing.copy(
                    accountName = account.profile?.name,
                    avatarUrl = account.profile?.avatarUrl,
                    isActiveAccount = account.localIndex == activeIndex,
                    needsReconnect = needsReconnect,
                )
            }
        }

        // Keep the seller's current page unless it vanished (disconnected) or nothing was
        // selected yet - an external active-account switch (Profile) must not yank the page.
        val previouslySelected = currentState().selectedLocalIndex
        val selected = previouslySelected
            ?.takeIf { index -> pages.any { it.localIndex == index } }
            ?: activeIndex
            ?: pages.firstOrNull()?.localIndex

        // A sheet open over an account that has just been disconnected has nowhere to act: every
        // action would resolve no token and fail with a generic error. Close it instead of
        // leaving a dead sheet in front of the seller.
        val openIndex = currentState().advertSheet?.localIndex
        val openAccountIsGone = openIndex != null && pages.none { it.localIndex == openIndex }

        setState {
            it.copy(
                pages = pages,
                selectedLocalIndex = selected,
                requiresOlxConnection = pages.isEmpty(),
                advertSheet = if (openAccountIsGone) null else it.advertSheet,
                actionConfirm = if (openAccountIsGone) null else it.actionConfirm,
                soldPrompt = if (openAccountIsGone) null else it.soldPrompt,
                advertEdit = if (openAccountIsGone) null else it.advertEdit,
            )
        }
        if (openAccountIsGone) editSnapshot = null

        val selectedPage = pages.find { it.localIndex == selected }
        if (selectedPage != null && !selectedPage.hasLoaded && !selectedPage.isLoading) {
            fetchPage(selectedPage.localIndex)
        }
    }

    private fun selectPage(localIndex: Int) {
        val current = currentState()
        if (current.selectedLocalIndex == localIndex) return
        val page = current.pages.find { it.localIndex == localIndex } ?: return

        setState { it.copy(selectedLocalIndex = localIndex) }
        if (!page.hasLoaded && !page.isLoading) {
            fetchPage(localIndex)
        }
    }

    private fun fetchPage(localIndex: Int) {
        viewModelScope.launch {
            setState { it.updatePage(localIndex) { page -> page.copy(isLoading = true, isLoadingMore = false, errorMessage = null) } }
            runCatching { repository.loadAdverts(localIndex = localIndex, offset = 0, limit = PageSize) }
                .onSuccess { adverts ->
                    setState {
                        it.updatePage(localIndex) { page ->
                            page.copy(
                                isLoading = false,
                                adverts = adverts.withPendingStatuses(),
                                canLoadMore = adverts.size == PageSize,
                                errorMessage = null,
                                hasLoaded = true,
                            )
                        }
                    }
                }
                .onFailure { error -> handlePageFailure(localIndex, error, isLoadMore = false) }
        }
    }

    private fun loadMore(localIndex: Int) {
        val page = currentState().pages.find { it.localIndex == localIndex } ?: return
        if (page.isLoading || page.isLoadingMore || !page.canLoadMore) return

        viewModelScope.launch {
            setState { it.updatePage(localIndex) { p -> p.copy(isLoadingMore = true, errorMessage = null) } }
            runCatching { repository.loadAdverts(localIndex = localIndex, offset = page.adverts.size, limit = PageSize) }
                .onSuccess { adverts ->
                    setState {
                        it.updatePage(localIndex) { p ->
                            p.copy(
                                isLoadingMore = false,
                                adverts = p.adverts + adverts.withPendingStatuses(),
                                canLoadMore = adverts.size == PageSize,
                            )
                        }
                    }
                }
                .onFailure { error -> handlePageFailure(localIndex, error, isLoadMore = true) }
        }
    }

    private suspend fun handlePageFailure(localIndex: Int, error: Throwable, isLoadMore: Boolean) {
        if (error is AccountNeedsReconnect) {
            // syncPages already reflects NeedsReconnect from the account store; just stop
            // spinning - the reconnect card, not a generic error, is what the page should show.
            setState {
                it.updatePage(localIndex) { page ->
                    if (isLoadMore) page.copy(isLoadingMore = false) else page.copy(isLoading = false, hasLoaded = true)
                }
            }
            return
        }

        val message = if (error is OlxApiException) {
            getString(Res.string.my_ads_load_failed_account, accountNameFor(localIndex))
        } else {
            error.message ?: getString(Res.string.my_ads_load_failed)
        }
        setState {
            it.updatePage(localIndex) { page ->
                if (isLoadMore) {
                    page.copy(isLoadingMore = false, errorMessage = message)
                } else {
                    page.copy(isLoading = false, errorMessage = message, hasLoaded = true)
                }
            }
        }
        postEffect(Effect.ShowMessage(message))
    }

    /**
     * Opening the sheet is what fetches the statistics (SIR-103). One call per advert, so
     * fetching for every visible row would be both slow and wasteful of OLX's per-IP request
     * budget - a seller with forty listings refreshing repeatedly is the case this avoids.
     */
    private fun openAdvertSheet(localIndex: Int, advert: MyAdvertItem) {
        val supportsExtend = _currentOlxCountry.supportsExtendCommand
        setState {
            it.copy(
                advertSheet = AdvertSheet(
                    localIndex = localIndex,
                    advert = advert,
                    actions = availableActions(advert.status, supportsExtend),
                    extendUnavailableHere = advert.status.isLive && !supportsExtend,
                    isLoadingStatistics = true,
                    pendingAction = inFlightActions[advert.id],
                ),
            )
        }
        loadStatistics(localIndex, advert.id)

        // Only these two states have anything to explain, and OLX's own words beat ours. This was
        // previously gated on the advert having no actions available, which stopped being true
        // for rejected listings once Delete was offered on every non-active status - so the
        // reason never actually showed for the listings it exists for.
        val state = advert.status.state
        if (state == AdvertState.Rejected || state == AdvertState.UnderReview) {
            loadModerationReason(localIndex, advert.id)
        }
    }

    private fun loadModerationReason(localIndex: Int, advertId: Long) {
        viewModelScope.launch {
            runCatching { lifecycleRepository.moderationReason(localIndex, advertId) }
                .onSuccess { reason ->
                    if (reason != null) updateSheet(advertId) { it.copy(olxReason = reason) }
                }
        }
    }

    private fun loadStatistics(localIndex: Int, advertId: Long) {
        viewModelScope.launch {
            runCatching { lifecycleRepository.statistics(localIndex, advertId) }
                .onSuccess { statistics ->
                    analytics.logEvent(
                        AnalyticsEvents.ADVERT_STATISTICS_VIEWED,
                        mapOf(
                            "advert_views_bucket" to AdvertAnalyticsBuckets.advertViews(statistics.advertViews),
                            "had_zero_views" to (statistics.advertViews == 0),
                        ),
                    )
                    updateSheet(advertId) {
                        it.copy(statistics = statistics, isLoadingStatistics = false, statisticsFailed = false)
                    }
                }
                .onFailure {
                    updateSheet(advertId) { it.copy(isLoadingStatistics = false, statisticsFailed = true) }
                }
        }
    }

    private fun openOnOlx() {
        val advert = currentState().advertSheet?.advert ?: return
        // Dismissed first: a modal sheet sitting over the screen swallows the browser hand-off.
        setState { it.copy(advertSheet = null) }

        // A listing OLX is still reviewing has no public URL yet, but the seller can still see it
        // among their own listings on OLX - so fall back to that page rather than refusing to go
        // anywhere. Same fallback the publish success screen already uses.
        val url = advert.url.ifBlank { "https://www.${_currentOlxCountry.domain}/myaccount/" }
        postEffect(Effect.OpenUrl(url))
    }

    /**
     * Routes each action to whatever has to happen before OLX is called.
     *
     * Deleting a live listing goes through the sold prompt as well as the delete confirmation:
     * OLX only accepts a delete on an inactive advert, so a live one has to be deactivated first,
     * and `deactivate` will not be accepted without an answer to "did it sell?".
     */
    private fun startAction(action: AdvertAction) {
        val sheet = currentState().advertSheet ?: return
        if (sheet.pendingAction != null || inFlightActions.containsKey(sheet.advert.id)) return

        when (action) {
            AdvertAction.Deactivate -> setState {
                it.copy(soldPrompt = SoldPrompt(sheet.localIndex, sheet.advert, thenDelete = false))
            }

            AdvertAction.Delete,
            AdvertAction.Reactivate,
            AdvertAction.Extend -> setState {
                it.copy(actionConfirm = ActionConfirm(sheet.localIndex, sheet.advert, action))
            }

            AdvertAction.Edit -> openEdit(sheet.localIndex, sheet.advert)
        }
    }

    private fun runConfirmedAction() {
        val confirm = currentState().actionConfirm ?: return
        setState { it.copy(actionConfirm = null) }

        if (confirm.action == AdvertAction.Delete && confirm.advert.status.isLive) {
            // The deactivate half of the delete still needs OLX's "did it sell?" answer.
            setState {
                it.copy(soldPrompt = SoldPrompt(confirm.localIndex, confirm.advert, thenDelete = true))
            }
            return
        }

        performAction(confirm.localIndex, confirm.advert, confirm.action, isSold = null, soldPrice = null)
    }

    private fun answerSold(isSold: Boolean) {
        val prompt = currentState().soldPrompt?.takeIf { !it.isSubmitting } ?: return
        if (isSold) {
            // Sold: ask what it went for. Not sold: no follow-up questions - a seller closing a
            // listing that failed does not want a form.
            setState { it.copy(soldPrompt = prompt.copy(askingPrice = true)) }
        } else {
            closeListing(prompt, isSold = false, soldPrice = null)
        }
    }

    private fun submitSoldPrice(price: Long?) {
        val prompt = currentState().soldPrompt?.takeIf { !it.isSubmitting } ?: return
        closeListing(prompt, isSold = true, soldPrice = price)
    }

    private fun closeListing(prompt: SoldPrompt, isSold: Boolean, soldPrice: Long?) {
        setState { it.copy(soldPrompt = prompt.copy(isSubmitting = true)) }
        performAction(
            localIndex = prompt.localIndex,
            advert = prompt.advert,
            action = if (prompt.thenDelete) AdvertAction.Delete else AdvertAction.Deactivate,
            isSold = isSold,
            soldPrice = soldPrice,
        )
    }

    private fun performAction(
        localIndex: Int,
        advert: MyAdvertItem,
        action: AdvertAction,
        isSold: Boolean?,
        soldPrice: Long?,
    ) {
        viewModelScope.launch {
            inFlightActions[advert.id] = action
            updateSheet(advert.id) { it.copy(pendingAction = action) }

            val result = runCatching {
                when (action) {
                    AdvertAction.Deactivate -> lifecycleRepository.deactivate(localIndex, advert.id, isSold == true)
                    AdvertAction.Reactivate -> lifecycleRepository.reactivate(localIndex, advert.id)
                    AdvertAction.Extend -> lifecycleRepository.extend(localIndex, advert.id)
                    AdvertAction.Delete -> lifecycleRepository.delete(localIndex, advert.id, advert.status.isLive, isSold)
                    // Edit never reaches here - it has its own sheet and its own submit path.
                    AdvertAction.Edit -> error("Edit is applied through submitEdit")
                }
            }

            // The seller's answer is only an outcome once OLX has actually taken the listing
            // down. A failed take-down leaves it live and buyable, so recording "sold" then
            // would describe a listing that is still running - and reporting it would count a
            // sale that has not closed, in the single metric this milestone exists to produce.
            // The seller retries, answers again, and it is recorded then.
            //
            // A half-done delete is the exception: its deactivate leg landed, so the listing IS
            // down and the outcome IS real, even though the delete leg failed.
            val closeLanded = result.isSuccess ||
                result.exceptionOrNull() is AdvertDeactivatedNotDeleted
            if (isSold != null && closeLanded) {
                outcomeStore.recordClosed(advert.id, isSold, soldPrice)
                logOutcome(advert.id, isSold, soldPrice)
            }

            setState { it.copy(soldPrompt = null) }
            inFlightActions.remove(advert.id)

            result
                .onSuccess { onActionSucceeded(localIndex, advert, action) }
                .onFailure { error -> onActionFailed(localIndex, advert, action, error) }
        }
    }

    private suspend fun onActionSucceeded(
        localIndex: Int,
        advert: MyAdvertItem,
        action: AdvertAction,
    ) {
        logAction(action, advert.status, "success")

        // A listing that is live again has no outcome. Without this, a listing closed as unsold,
        // reactivated, and later genuinely sold would never log `advert_sold` - the one metric
        // this milestone exists to produce - because a close was already on record for it.
        if (action == AdvertAction.Reactivate) {
            outcomeStore.clearOutcome(advert.id)
        }

        if (action == AdvertAction.Delete) {
            setState {
                it.copy(
                    // Only this advert's sheet: the seller may have dismissed it mid-delete and
                    // opened another listing, which must not vanish from under them.
                    advertSheet = it.advertSheet?.takeIf { sheet -> sheet.advert.id != advert.id },
                    pages = it.pages.map { page ->
                        if (page.localIndex == localIndex) {
                            page.copy(adverts = page.adverts.filterNot { row -> row.id == advert.id })
                        } else {
                            page
                        }
                    },
                )
            }
            pendingStatuses.remove(advert.id)
            postEffect(Effect.ShowMessage(getString(Res.string.advert_action_done_delete)))
            fetchPage(localIndex)
            return
        }

        // Close the sheet. Leaving it open showed the seller the same buttons they had just
        // pressed, which reads as nothing having happened - and the confirmation snackbar is
        // rendered by the screen underneath, so while the sheet is up it is not even visible.
        setState { it.copy(advertSheet = it.advertSheet?.takeIf { sheet -> sheet.advert.id != advert.id }) }

        // Applied before the message, so what the seller sees never lags what they are told.
        expectedStatusAfter(action)?.let { expected ->
            pendingStatuses[advert.id] = PendingStatus(actedFrom = advert.status, expected = expected)
            setStatusLocally(localIndex, advert.id, expected)
        }

        postEffect(Effect.ShowMessage(getString(successMessageFor(action))))

        // Every action refetches, so anything the command changed that cannot be anticipated -
        // an extend's new expiry, an edit's price - actually appears. The expectation recorded
        // above is what stops OLX's momentarily-stale status from undoing the row.
        fetchPage(localIndex)
    }

    /**
     * The status OLX's documented lifecycle puts an advert in after [action] succeeds, or null
     * when the action does not change status. `deactivate` is the seller taking their own listing
     * down, which OLX reports as `removed_by_user`; `activate` puts it back to `active`.
     */
    private fun expectedStatusAfter(action: AdvertAction): AdvertStatus? = when (action) {
        AdvertAction.Deactivate -> AdvertStatus.RemovedByUser
        AdvertAction.Reactivate -> AdvertStatus.Active
        AdvertAction.Extend,
        AdvertAction.Delete,
        AdvertAction.Edit -> null
    }

    /**
     * Overlays [pendingStatuses] onto rows just loaded from OLX, and retires each entry as soon
     * as the server stops reporting the status it was recorded against.
     */
    private fun List<MyAdvertItem>.withPendingStatuses(): List<MyAdvertItem> {
        if (pendingStatuses.isEmpty()) return this

        return map { row ->
            val pending = pendingStatuses[row.id] ?: return@map row
            if (row.status == pending.actedFrom) {
                // Still the status we acted on, so OLX has not caught up yet.
                row.copy(status = pending.expected)
            } else {
                pendingStatuses.remove(row.id)
                row
            }
        }
    }

    private fun setStatusLocally(localIndex: Int, advertId: Long, status: AdvertStatus) {
        setState { state ->
            state.updatePage(localIndex) { page ->
                page.copy(
                    adverts = page.adverts.map { row ->
                        if (row.id == advertId) row.copy(status = status) else row
                    },
                )
            }
        }
    }

    private suspend fun onActionFailed(
        localIndex: Int,
        advert: MyAdvertItem,
        action: AdvertAction,
        error: Throwable,
    ) {
        analytics.recordException(error, AnalyticsEvents.ADVERT_ACTION)

        if (error is AdvertDeactivatedNotDeleted) {
            // Half of the delete landed. Saying "couldn't delete" alone would leave the seller
            // thinking their listing is still live when it is already down.
            logAction(action, advert.status, "partial")
            setState { it.copy(advertSheet = it.advertSheet?.takeIf { sheet -> sheet.advert.id != advert.id }) }
            postEffect(Effect.ShowMessage(getString(Res.string.advert_delete_partial)))
            fetchPage(localIndex)
            return
        }

        val olxError = (error as? OlxApiException)?.error
        logAction(action, advert.status, if (olxError is OlxApiError.ValidationError) "rejected" else "failed")
        updateSheet(advert.id) { it.copy(pendingAction = null) }

        // OLX resolved the status differently from what this app believed, so re-read the list -
        // a rejected action usually means the mapping was working from a stale status. Any
        // expectation for this advert is dropped first: it was evidently wrong.
        if (olxError is OlxApiError.ValidationError) {
            pendingStatuses.remove(advert.id)
            fetchPage(localIndex)
        }

        postEffect(Effect.ShowMessage(actionFailureMessage(localIndex, error)))
    }

    /**
     * Only OLX's own words are quoted back to the seller. `ValidationError.fieldDetail` is text
     * OLX put in the response ("Ad has to be active"); every other [OlxApiError.userMessage] is
     * this app's English developer diagnostic, which must never reach the UI - see the language
     * policy on [OlxApiError].
     */
    private suspend fun actionFailureMessage(localIndex: Int, error: Throwable): String {
        val olxError = (error as? OlxApiException)?.error
        return when {
            olxError is OlxApiError.ValidationError ->
                getString(Res.string.advert_action_failed, olxError.fieldDetail)

            // An account whose token is genuinely dead cannot be retried into working. Telling
            // the seller to "try again in a moment" would send them round a loop that can only
            // fail until they reconnect.
            error is AccountNeedsReconnect ||
                olxError is OlxApiError.InvalidToken ||
                olxError is OlxApiError.InvalidGrant ->
                getString(Res.string.advert_action_needs_reconnect, accountNameFor(localIndex))

            else -> getString(Res.string.advert_action_failed_generic)
        }
    }

    private fun openEdit(localIndex: Int, advert: MyAdvertItem) {
        // Dropped before the load starts, not after it fails: `applyEdit` takes the advert id
        // from the snapshot, so a snapshot left over from a previously edited listing would send
        // this seller's changes to that other advert.
        editSnapshot = null
        setState { it.copy(advertEdit = AdvertEdit(localIndex = localIndex, advert = advert)) }

        viewModelScope.launch {
            runCatching { lifecycleRepository.loadForEdit(localIndex, advert.id) }
                .onSuccess { snapshot ->
                    // Both halves are gated on the sheet still editing THIS advert. A slow load
                    // for a listing the seller has since backed out of would otherwise seed the
                    // form with that listing's title and description, and the next Save would
                    // push them onto whichever advert is open now.
                    if (!isEditing(advert.id)) return@onSuccess
                    editSnapshot = snapshot
                    setState { state ->
                        state.copy(
                            advertEdit = state.advertEdit?.copy(
                                isLoading = false,
                                title = snapshot.detail.title,
                                description = snapshot.detail.description,
                                priceValue = snapshot.detail.price?.value,
                            ),
                        )
                    }
                }
                .onFailure {
                    if (!isEditing(advert.id)) return@onFailure
                    editSnapshot = null
                    setState { state ->
                        state.copy(advertEdit = state.advertEdit?.copy(isLoading = false, loadFailed = true))
                    }
                    postEffect(Effect.ShowMessage(getString(Res.string.advert_edit_load_failed)))
                }
        }
    }

    private fun isEditing(advertId: Long): Boolean = currentState().advertEdit?.advert?.id == advertId

    private fun submitEdit(title: String, description: String, price: Long?) {
        val edit = currentState().advertEdit ?: return
        // Belt and braces on the same hazard as `openEdit`: never PUT a payload that describes a
        // different advert from the one the open sheet is editing.
        val snapshot = editSnapshot?.takeIf { it.detail.id == edit.advert.id } ?: return
        if (edit.isSaving) return

        // A cleared price field means "leave the price as it is". OLX has no way to remove a
        // price through this payload, so counting an empty field as a change would report an
        // edit - and log a price-only edit - that never touched the price.
        val newPrice = price?.takeIf { it != edit.priceValue }
        val fieldsChanged = listOf(
            title != edit.title,
            description != edit.description,
            newPrice != null,
        ).count { it }
        if (fieldsChanged == 0) {
            setState { it.copy(advertEdit = null) }
            return
        }

        setState { it.copy(advertEdit = edit.copy(isSaving = true)) }

        viewModelScope.launch {
            runCatching {
                lifecycleRepository.applyEdit(
                    localIndex = edit.localIndex,
                    snapshot = snapshot,
                    title = title,
                    description = description,
                    priceValue = newPrice,
                    fallbackCurrencyCode = edit.advert.currencyCode.ifBlank { _currentOlxCountry.currencyCode },
                )
            }
                .onSuccess { refreshed: MyAdvertItem? ->
                    analytics.logEvent(
                        AnalyticsEvents.ADVERT_EDITED,
                        mapOf(
                            "fields_changed" to fieldsChanged,
                            "was_price_only" to (fieldsChanged == 1 && newPrice != null),
                        ),
                    )
                    logAction(AdvertAction.Edit, edit.advert.status, "success")
                    editSnapshot = null
                    // The edit sheet and the actions sheet underneath both close; the refreshed
                    // list is the feedback.
                    setState { state ->
                        state.copy(
                            advertEdit = null,
                            advertSheet = state.advertSheet?.takeIf { it.advert.id != edit.advert.id },
                        )
                    }
                    // An edit can send an advert back to moderation. Saying so beats letting the
                    // status change surprise the seller later. Unknown when the row could not be
                    // re-read, in which case the plain confirmation is the honest one.
                    val message = if (refreshed?.status?.state == AdvertState.UnderReview) {
                        Res.string.advert_action_done_edit_moderated
                    } else {
                        Res.string.advert_action_done_edit
                    }
                    postEffect(Effect.ShowMessage(getString(message)))
                    fetchPage(edit.localIndex)
                }
                .onFailure { error ->
                    analytics.recordException(error, AnalyticsEvents.ADVERT_ACTION)
                    val olxError = (error as? OlxApiException)?.error
                    logAction(
                        AdvertAction.Edit,
                        edit.advert.status,
                        if (olxError is OlxApiError.ValidationError) "rejected" else "failed",
                    )
                    setState { state -> state.copy(advertEdit = state.advertEdit?.copy(isSaving = false)) }
                    postEffect(Effect.ShowMessage(actionFailureMessage(edit.localIndex, error)))
                }
        }
    }

    /** A no-op once the seller has closed the sheet or opened a different advert in it. */
    private fun updateSheet(advertId: Long, transform: (AdvertSheet) -> AdvertSheet) {
        setState { state ->
            val sheet = state.advertSheet
            if (sheet == null || sheet.advert.id != advertId) state else state.copy(advertSheet = transform(sheet))
        }
    }

    private fun logAction(action: AdvertAction, fromStatus: AdvertStatus, result: String) {
        analytics.logEvent(
            AnalyticsEvents.ADVERT_ACTION,
            mapOf(
                // OLX's own command name, so the event reads the same way as the API it reports
                // on - `activate`, not this app's `Reactivate`.
                "action" to when (action) {
                    AdvertAction.Reactivate -> AdvertCommand.Activate.wireValue
                    AdvertAction.Deactivate -> AdvertCommand.Deactivate.wireValue
                    AdvertAction.Extend -> AdvertCommand.Extend.wireValue
                    AdvertAction.Delete -> "delete"
                    AdvertAction.Edit -> "edit"
                },
                "from_status" to fromStatus.name.lowercase(),
                "result" to result,
            ),
        )
    }

    /**
     * The measurement the milestone exists for: `price_delta_percent_bucket` is the gap between
     * the AI's suggestion and what the item actually sold for. Bucketed, never absolute, so no
     * individual sale can be reconstructed from the event stream.
     */
    private suspend fun logOutcome(advertId: Long, isSold: Boolean, soldPrice: Long?) {
        val outcome = outcomeStore.outcomeFor(advertId)
        val daysLive = outcome?.daysLive

        if (!isSold) {
            analytics.logEvent(
                AnalyticsEvents.ADVERT_CLOSED_UNSOLD,
                buildMap {
                    daysLive?.let { put("days_live_bucket", AdvertAnalyticsBuckets.daysLive(it)) }
                },
            )
            return
        }

        analytics.logEvent(
            AnalyticsEvents.ADVERT_SOLD,
            buildMap {
                put("had_price_entered", soldPrice != null)
                daysLive?.let { put("days_live_bucket", AdvertAnalyticsBuckets.daysLive(it)) }
                outcome?.priceDeltaPercent?.let {
                    put("price_delta_percent_bucket", AdvertAnalyticsBuckets.priceDelta(it))
                }
            },
        )
    }

    private suspend fun accountNameFor(localIndex: Int): String =
        currentState().pages.find { it.localIndex == localIndex }
            ?.accountName
            ?.takeIf { it.isNotBlank() }
            ?: getString(Res.string.my_ads_account_fallback_name)

    private fun successMessageFor(action: AdvertAction) = when (action) {
        AdvertAction.Deactivate -> Res.string.advert_action_done_deactivate
        AdvertAction.Reactivate -> Res.string.advert_action_done_reactivate
        AdvertAction.Delete -> Res.string.advert_action_done_delete
        AdvertAction.Extend -> Res.string.advert_action_done_extend
        AdvertAction.Edit -> Res.string.advert_action_done_edit
    }
}


/** Mutates exactly one page by [localIndex], never by list position - a no-op if that page has
 * since vanished (account disconnected mid-flight), so a stale result for it is dropped. */
private fun State.updatePage(localIndex: Int, transform: (AccountPage) -> AccountPage): State =
    copy(pages = pages.map { if (it.localIndex == localIndex) transform(it) else it })
