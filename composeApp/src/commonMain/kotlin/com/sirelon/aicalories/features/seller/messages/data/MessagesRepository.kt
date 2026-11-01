package com.sirelon.sellsnap.features.seller.messages.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.OlxThread
import com.sirelon.sellsnap.features.seller.my_ads.data.withAccountToken
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ThreadsPageSize = 50

/** The account the app is acting as in the current country, or null when none is connected. */
internal fun OlxAccountsRecord.activeAccount(): OlxAccountRecord? {
    val country = _currentOlxCountry.code
    val activeIndex = activeByCountry[country] ?: return null
    return accounts.find { it.localIndex == activeIndex && it.countryCode == country }
}

/**
 * Read-only buyer messages. Registered as a singleton because it also holds the unread total the
 * Messages tab badge shows, which the list screen and the badge both write.
 */
class MessagesRepository internal constructor(
    private val accountRepository: SellerAccountRepository,
    private val unauthenticatedOlxApiClient: OlxApiClient,
) {
    private val _unreadCount = MutableStateFlow(0)

    /** Unread messages across the first page of seller-side threads, as of the last load. */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /**
     * The first page of the account's conversations, in the order OLX returns them (it sorts by
     * last activity, which the schema does not expose, so there is nothing to sort by here).
     *
     * Threads where the account is the BUYER are dropped: on those `interlocutor_id` is the
     * account's own user id (SIR-88, olx.ua). The unread total is summed after that filter.
     * Beyond the first [ThreadsPageSize] threads nothing is loaded.
     */
    suspend fun loadThreads(localIndex: Int): List<OlxThread> {
        val threads = accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getThreads(accessToken, offset = 0, limit = ThreadsPageSize)
        }
        val ownUserId = accountRepository.accountsRecordFlow.value.accounts
            .find { it.localIndex == localIndex }
            ?.olxUserId
        val sellerThreads = threads.filter { ownUserId == null || it.interlocutorId != ownUserId }
        _unreadCount.value = sellerThreads.sumOf { it.unreadCount }
        return sellerThreads
    }

    suspend fun loadMessages(localIndex: Int, threadId: Long): List<OlxMessage> =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getThreadMessages(accessToken, threadId)
        }

    /** For when there is no account to count for. */
    fun clearUnread() {
        _unreadCount.value = 0
    }
}
