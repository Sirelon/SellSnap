package com.sirelon.sellsnap.features.seller.messages.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data._currentOlxCountry
import com.sirelon.sellsnap.features.seller.messages.domain.OlxMessage
import com.sirelon.sellsnap.features.seller.messages.domain.ThreadsPage
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

    /**
     * Unread messages across the seller-side threads the list screen has loaded (pages of
     * [ThreadsPageSize], more as the seller asks for them), or the first page when only the badge
     * has loaded. Threads on pages nobody has loaded are not counted.
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
     * Callers publish the unread total themselves with [publishUnread].
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
        return ThreadsPage(
            threads = threads.filter { ownUserId == null || it.interlocutorId != ownUserId },
            canLoadMore = threads.size == ThreadsPageSize,
        )
    }

    suspend fun loadMessages(localIndex: Int, threadId: Long): List<OlxMessage> =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getThreadMessages(accessToken, threadId)
        }

    fun publishUnread(count: Int) {
        _unreadCount.value = count
    }

    /** For when there is no account to count for. */
    fun clearUnread() = publishUnread(0)
}
