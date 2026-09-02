package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository

/** Thrown when [MyAdvertsRepository.loadAdverts] has no usable token because the account is
 * genuinely gone or [OlxAccountState.NeedsReconnect] - as opposed to a transient refresh failure
 * (network blip, 5xx), which surfaces as a regular [OlxApiException] instead. The ViewModel maps
 * this one to that page's reconnect state rather than a generic, retryable load error. */
class AccountNeedsReconnect : Exception("This OLX account needs to reconnect.")

class MyAdvertsRepository(
    private val accountRepository: SellerAccountRepository,
    private val unauthenticatedOlxApiClient: OlxApiClient,
) {
    /**
     * Explicit-token fetch (SIR-87): every page, including the active account's, goes through
     * [SellerAccountRepository.accessTokenFor] on the unauthenticated client rather than the
     * shared authorized client, which only ever serves whichever account is globally active. On
     * an [OlxApiError.InvalidToken] response, retries exactly once with a forced refresh -
     * reproducing the bearer plugin's single reactive refresh rather than looping (OLX login
     * attempts are rate-limited).
     */
    suspend fun loadAdverts(localIndex: Int, offset: Int, limit: Int): List<MyAdvertItem> {
        val accessToken = accountRepository.accessTokenFor(localIndex) ?: throw noTokenException(localIndex)

        return try {
            fetchAdverts(accessToken, offset, limit)
        } catch (exception: OlxApiException) {
            if (exception.error !is OlxApiError.InvalidToken) throw exception
            val refreshedToken = accountRepository.accessTokenFor(localIndex, forceRefresh = true)
                ?: throw noTokenException(localIndex)
            fetchAdverts(refreshedToken, offset, limit)
        }
    }

    private suspend fun fetchAdverts(accessToken: String, offset: Int, limit: Int): List<MyAdvertItem> =
        unauthenticatedOlxApiClient
            .getCurrentUserAdverts(accessToken, offset = offset, limit = limit)
            .map(MyAdvertItemMapper::map)

    /**
     * [SellerAccountRepository.accessTokenFor] returns null both for a genuinely dead account and
     * for a transient refresh failure it swallowed (network blip, 5xx - only a terminal
     * `invalid_grant`/`invalid_token` flips the account to [OlxAccountState.NeedsReconnect]).
     * Re-reads the account's *current* state after the attempt to tell them apart, so a network
     * blip surfaces as a retryable error instead of a false "needs reconnect" or, worse, a false
     * "no listings yet" empty state.
     */
    private fun noTokenException(localIndex: Int): Exception {
        val needsReconnect = accountRepository.accountsRecordFlow.value.accounts
            .find { it.localIndex == localIndex }
            ?.state == OlxAccountState.NeedsReconnect
        return if (needsReconnect) {
            AccountNeedsReconnect()
        } else {
            OlxApiException(OlxApiError.NetworkFailure("Could not refresh the OLX session for this account."))
        }
    }
}
