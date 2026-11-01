package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.messages.data.activeAccount
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Unread total for the Messages tab badge. It reloads the first page of threads when the app
 * comes to the foreground and when the active account changes; the Messages list refreshes the
 * same total whenever it loads, counting every page it holds. A failed reload leaves the last
 * total, and nothing is shown.
 */
class MessagesBadgeViewModel internal constructor(
    private val repository: MessagesRepository,
    private val accountRepository: SellerAccountRepository,
) : ViewModel() {

    val unreadCount: StateFlow<Int> = repository.unreadCount

    private var refreshJob: Job? = null

    init {
        accountRepository
            .accountsRecordFlow
            .map { it.activeAccount()?.localIndex }
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        refreshJob?.cancel()
        val account = accountRepository.accountsRecordFlow.value.activeAccount()
        if (account == null || account.state == OlxAccountState.NeedsReconnect) {
            repository.clearUnread()
            return
        }
        refreshJob = viewModelScope.launch {
            runCatching {
                repository.publishUnread(repository.loadThreads(account.localIndex).threads.sumOf { it.unreadCount })
            }
        }
    }
}
