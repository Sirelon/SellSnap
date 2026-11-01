package com.sirelon.sellsnap.features.seller.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.messages.data.countryAccounts
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Unread total for the Messages tab badge: the sum over every connected account of the current
 * country, except accounts that need a reconnect. It reloads the first page of threads of each of
 * those accounts when the app comes to the foreground and when the set of accounts changes; the
 * Messages list refreshes an account's count whenever it loads, counting every page it holds. A
 * failed reload leaves that account's last count, and nothing is shown.
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
            .map { it.countedAccounts() }
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        refreshJob?.cancel()
        val accounts = accountRepository.accountsRecordFlow.value.countedAccounts()
        repository.retainUnread(accounts)
        refreshJob = viewModelScope.launch {
            accounts.forEach { localIndex ->
                launch {
                    runCatching {
                        val threads = repository.loadThreads(localIndex).threads
                        repository.publishUnread(localIndex, threads.sumOf { it.unreadCount })
                    }
                }
            }
        }
    }

    private fun OlxAccountsRecord.countedAccounts(): Set<Int> = countryAccounts()
        .filter { it.state != OlxAccountState.NeedsReconnect }
        .mapTo(LinkedHashSet()) { it.localIndex }
}
