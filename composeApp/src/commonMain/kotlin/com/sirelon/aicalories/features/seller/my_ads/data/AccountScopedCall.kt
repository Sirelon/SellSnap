package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository

/** Thrown when an account-scoped call has no usable token because the account is genuinely gone
 * or [OlxAccountState.NeedsReconnect] - as opposed to a transient refresh failure (network blip,
 * 5xx), which surfaces as a regular [OlxApiException] instead. The ViewModel maps this one to
 * that page's reconnect state rather than a generic, retryable load error. */
class AccountNeedsReconnect : Exception("This OLX account needs to reconnect.")

/**
 * Runs [block] with a usable access token for one stored account.
 *
 * My Ads shows every connected account at once, so nothing here may go through the shared
 * authorized client - that one only ever serves whichever account is globally active (SIR-87).
 * Every call resolves its own token via [SellerAccountRepository.accessTokenFor] and, on an
 * [OlxApiError.InvalidToken] response, retries exactly once with a forced refresh - reproducing
 * the bearer plugin's single reactive refresh rather than looping, since OLX login attempts are
 * rate-limited.
 */
internal suspend fun <T> SellerAccountRepository.withAccountToken(
    localIndex: Int,
    block: suspend (accessToken: String) -> T,
): T {
    val accessToken = accessTokenFor(localIndex) ?: throw noTokenException(localIndex)

    return try {
        block(accessToken)
    } catch (exception: OlxApiException) {
        if (exception.error !is OlxApiError.InvalidToken) throw exception
        val refreshedToken = accessTokenFor(localIndex, forceRefresh = true)
            ?: throw noTokenException(localIndex)
        block(refreshedToken)
    }
}

/**
 * [SellerAccountRepository.accessTokenFor] returns null both for a genuinely dead account and for
 * a transient refresh failure it swallowed (network blip, 5xx - only a terminal
 * `invalid_grant`/`invalid_token` flips the account to [OlxAccountState.NeedsReconnect]).
 * Re-reads the account's *current* state after the attempt to tell them apart, so a network blip
 * surfaces as a retryable error instead of a false "needs reconnect" or, worse, a false
 * "no listings yet" empty state.
 */
private fun SellerAccountRepository.noTokenException(localIndex: Int): Exception {
    val needsReconnect = accountsRecordFlow.value.accounts
        .find { it.localIndex == localIndex }
        ?.state == OlxAccountState.NeedsReconnect
    return if (needsReconnect) {
        AccountNeedsReconnect()
    } else {
        OlxApiException(OlxApiError.NetworkFailure("Could not refresh the OLX session for this account."))
    }
}
