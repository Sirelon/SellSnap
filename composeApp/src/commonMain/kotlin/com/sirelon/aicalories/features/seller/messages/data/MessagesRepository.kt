package com.sirelon.sellsnap.features.seller.messages.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.ThreadCommand
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.ThreadsPage
import com.sirelon.sellsnap.features.seller.my_ads.data.withAccountToken
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ThreadsPageSize = 50

/** The account the app is acting as in the current country, or null when none is connected. */
internal fun OlxAccountsRecord.activeAccount(): OlxAccountRecord? {
    val country = _currentOlxCountry.code
    val activeIndex = activeByCountry[country] ?: return null
    return accounts.find { it.localIndex == activeIndex && it.countryCode == country }
}

/** Every account connected in the current country, in the order My Ads lists them. */
internal fun OlxAccountsRecord.countryAccounts(): List<OlxAccountRecord> {
    val country = _currentOlxCountry.code
    return accounts.filter { it.countryCode == country }.sortedBy { it.localIndex }
}

/** A thread the seller marked as read, so the list can clear its row without waiting for a poll. */
internal class ThreadMarkedRead(val localIndex: Int, val threadId: Long)

/**
 * Buyer messages: read, and marked as read on the seller's explicit request - nothing here sends a
 * command on its own. Registered as a singleton because it also holds the unread counts the
 * Messages tab badge shows, which the list screen and the badge both write.
 */
class MessagesRepository internal constructor(
    private val accountRepository: SellerAccountRepository,
    private val unauthenticatedOlxApiClient: OlxApiClient,
) {
    /** Unread messages per thread id, per account (by localIndex). */
    private var unreadByAccount = emptyMap<Int, Map<Long, Int>>()
    private val _unreadCount = MutableStateFlow(0)
    private val _markedRead = MutableSharedFlow<ThreadMarkedRead>(extraBufferCapacity = 1)

    /** Emits after OLX accepted a mark-as-read command; the list screen clears that row. */
    internal val markedRead: SharedFlow<ThreadMarkedRead> = _markedRead

    /**
     * Unread messages summed across accounts. Per account: the seller-side threads the list screen
     * has loaded (pages of [ThreadsPageSize], more as the seller asks for them), or the first page
     * when only the badge has loaded. Threads on pages nobody has loaded are not counted.
     */
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    /**
     * One page of the account's conversations ([page] 0 is the first), in the order OLX returns
     * them (it sorts by last activity, which the schema does not expose, so there is nothing to
     * sort by here).
     *
     * Threads where the account is the BUYER are dropped from every page: on those
     * `interlocutor_id` is the account's own user id (SIR-88, olx.ua). [ThreadsPage.canLoadMore]
     * is decided before that filter, from whether OLX returned a full page.
     * Callers publish the account's unread total themselves with [publishUnread].
     */
    suspend fun loadThreads(localIndex: Int, page: Int = 0): ThreadsPage {
        val threads = accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getThreads(
                accessToken,
                offset = page * ThreadsPageSize,
                limit = ThreadsPageSize,
            )
        }
        val ownUserId = accountRepository.accountsRecordFlow.value.accounts
            .find { it.localIndex == localIndex }
            ?.olxUserId
        val sellerSide = threads.filter { ownUserId == null || it.interlocutorId != ownUserId }
        return ThreadsPage(
            threads = sellerSide,
            canLoadMore = threads.size == ThreadsPageSize,
            buyerSideCount = threads.size - sellerSide.size,
        )
    }

    suspend fun loadMessages(localIndex: Int, threadId: Long): List<OlxMessage> =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getThreadMessages(accessToken, threadId)
        }

    /**
     * `POST threads/{id}/commands` `mark-as-read` with the token of the account at [localIndex].
     * On success that thread counts as zero unread (the tab badge drops now) and [markedRead]
     * emits. Reading a thread does not clear OLX's `unread_count` (SIR-88), so this is the only
     * way a thread stops being unread; call it only from an explicit seller action.
     */
    suspend fun markAsRead(localIndex: Int, threadId: Long) {
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.sendThreadCommand(accessToken, threadId, ThreadCommand.MarkAsRead)
        }
        unreadByAccount[localIndex]?.let { threads ->
            publishUnread(localIndex, threads + (threadId to 0))
        }
        _markedRead.tryEmit(ThreadMarkedRead(localIndex, threadId))
    }

    /** Replaces the account's unread counts with [unreadByThread], the unread count of each thread loaded. */
    fun publishUnread(localIndex: Int, unreadByThread: Map<Long, Int>) {
        unreadByAccount = unreadByAccount + (localIndex to unreadByThread)
        updateTotal()
    }

    /** Drops the counts of every account not in [localIndexes]: disconnected, or needing a reconnect. */
    fun retainUnread(localIndexes: Set<Int>) {
        unreadByAccount = unreadByAccount.filterKeys { it in localIndexes }
        updateTotal()
    }

    private fun updateTotal() {
        _unreadCount.value = unreadByAccount.values.sumOf { threads -> threads.values.sum() }
    }
}
