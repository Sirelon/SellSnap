package com.sirelon.sellsnap.features.seller.profile.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.media.upload.DraftMediaFileStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountState
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountsRecord
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxConfig
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxProfileSnapshot
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.refreshOlxTokens
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthorizationRequest
import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import com.sirelon.sellsnap.features.seller.auth.domain.OlxSessionState
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.features.seller.auth.domain.OlxUser
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertOutcomeStore
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * Multi-account orchestration for OLX sellers (SIR-83). Wraps [OlxAuthRepository] (OAuth/session
 * mode), [OlxApiClient] (REST resources), and [OlxAccountStore] (the persisted multi-account
 * record), and is the ONLY place allowed to change which account is active for a country - every
 * path that does so (add-account completion, disconnecting the active account, a successful
 * reconnect, and eventually the Profile switcher UI) must route through [setActiveAccount].
 */
class SellerAccountRepository internal constructor(
    private val authRepository: OlxAuthRepository,
    private val olxApiClient: OlxApiClient,
    private val unauthenticatedOlxApiClient: OlxApiClient,
    private val authorizedHttpClient: HttpClient,
    private val unauthenticatedHttpClient: HttpClient,
    private val accountStore: OlxAccountStore,
    private val locationRepository: LocationRepository,
    private val olxCountryStore: OlxCountryStore,
    private val draftMediaFileStore: DraftMediaFileStore,
    private val advertOutcomeStore: AdvertOutcomeStore,
    private val analyticsConsentRepository: AnalyticsConsentRepository,
    private val errorParser: OlxRemoteErrorParser,
    private val analytics: Analytics,
) {
    private val _user = MutableStateFlow<OlxUser?>(null)
    val user: StateFlow<OlxUser?> = _user.asStateFlow()

    /** Bumped by [setActiveAccount] and by disconnecting the active account. In-flight
     * account-scoped loads (My Ads, publish) should snapshot this before a load and compare it
     * afterward, discarding the result if it changed underneath them. */
    private val _switchEpoch = MutableStateFlow(0)
    val switchEpoch: StateFlow<Int> = _switchEpoch.asStateFlow()

    /** Pass-through of [OlxAuthRepository.sessionModeFlow] for callers (e.g. Profile) that only
     * depend on this repository rather than on [OlxAuthRepository] directly. */
    val sessionModeFlow: Flow<SellerSessionMode> get() = authRepository.sessionModeFlow

    suspend fun currentSession(): OlxSessionState = authRepository.currentSession()

    suspend fun createAuthorizationRequest(forceReauth: Boolean = false): OlxAuthorizationRequest =
        authRepository.createAuthorizationRequest(forceReauth)

    /** Pure UI gate for the "Add OLX account" button/action - at most [MAX_ACCOUNTS_PER_COUNTRY]
     * accounts per country. [addAccount] also re-checks this for a genuinely new account right
     * before persisting, so a stale UI state can't race past it into a 4th account. */
    fun canAddAccount(countryCode: String): Boolean =
        accountCountFor(countryCode) < MAX_ACCOUNTS_PER_COUNTRY

    /** Accessor for the Publish flow (SIR-83 item 7 reference): the active account's identifiers,
     * with no PII beyond what publish already needs to detect a mismatch. */
    internal fun activeAccountSnapshot(countryCode: String): OlxAccountRecord? {
        val record = accountStore.recordFlow.value
        val activeIndex = record.activeByCountry[countryCode] ?: return null
        return record.accounts.find { it.localIndex == activeIndex }
    }

    /** Raw multi-account record for UI that renders the full accounts list (Profile) or an
     * account picker (Publish) - filtering to one country, sorting active-first, etc. is left to
     * the caller, which already has [OlxCountryStore]/[olxCountryStore] to know the active country. */
    internal val accountsRecordFlow: StateFlow<OlxAccountsRecord> get() = accountStore.recordFlow

    /** Read-through so callers don't need [OlxAccountStore] injected directly. Pass `localIndex =
     * null` for the country-keyed pending-attempt counter (first connect / add, no account yet). */
    internal fun consecutiveAuthFailures(localIndex: Int?, countryCode: String): Int =
        accountStore.consecutiveFailureCount(localIndex, countryCode)

    /** Seconds remaining in the 60s post-failure cooldown (PRD U3), or 0 if none is in force. */
    internal fun remainingCooldownSeconds(localIndex: Int?, countryCode: String): Long =
        accountStore.remainingCooldownSeconds(localIndex, countryCode, nowEpochSeconds())

    /**
     * The ONLY function allowed to change which account is active for a country. Clears the
     * authorized client's cached bearer token (else it keeps serving the previous account's
     * token) and bumps [switchEpoch] so in-flight account-scoped loads can detect the switch.
     */
    suspend fun setActiveAccount(
        countryCode: String,
        localIndex: Int,
        fromPublishScreen: Boolean = false,
    ) {
        val previousIndex = accountStore.recordFlow.value.activeByCountry[countryCode]
        accountStore.setActive(countryCode, localIndex)
        clearActiveAccountClientState(countryCode)
        analytics.logEvent(
            AnalyticsEvents.ACCOUNT_SWITCHED,
            mapOf(
                "from_index" to (previousIndex ?: -1),
                "to_index" to localIndex,
                "account_count" to accountCountFor(countryCode),
                "from_publish_screen" to fromPublishScreen,
            ),
        )
    }

    /**
     * Add-account completion (also used for reconnect - see class doc on [AddAccountOutcome]).
     * Runs exactly once per seller tap, no automatic retry (PRD U3): exchange the authorization
     * code -> call `users/me` with an explicit bearer token on the UNauthenticated client (the
     * token is deliberately not in the account store yet, since whether it's a duplicate isn't
     * known until this call returns) -> persist/dedupe -> make it active.
     *
     * A `ReconnectedDuplicate` result means the callback resolved to an OLX user that was already
     * connected (possibly a `NeedsReconnect` one): [OlxAccountStore.addOrUpdateAccount] already
     * refreshes its tokens and flips its state back to `Usable` in that case, so no separate
     * "reconnect" method is needed - reconnecting a dead account is just another add-account
     * attempt that happens to dedupe against an existing localIndex.
     */
    internal suspend fun addAccount(callbackUrl: String): AddAccountOutcome {
        val countryCode = olxCountryStore.current.code
        analytics.logEvent(
            AnalyticsEvents.ACCOUNT_ADD_STARTED,
            mapOf("country" to countryCode, "existing_account_count" to accountCountFor(countryCode)),
        )

        val tokens = try {
            authRepository.exchangeAuthorizationCallback(callbackUrl)
        } catch (error: Throwable) {
            return addAccountFailure(countryCode, AddAccountFailureReason.Authorization(error.toOlxApiError()))
        }

        val profile = try {
            unauthenticatedOlxApiClient.getAuthenticatedUser(tokens.accessToken)
        } catch (error: Throwable) {
            return addAccountFailure(countryCode, AddAccountFailureReason.Authorization(error.toOlxApiError()))
        }

        val existingAccountForUser = accountStore.recordFlow.value.accounts.find {
            it.countryCode == countryCode && it.olxUserId == profile.id
        }
        if (existingAccountForUser == null && !canAddAccount(countryCode)) {
            // A genuinely new account would exceed the cap - reject without persisting. Not
            // treated as an auth failure/cooldown case: OLX granted a perfectly valid token, we
            // are the ones declining it, so it shouldn't throttle the seller's next attempt.
            return addAccountFailure(countryCode, AddAccountFailureReason.AccountLimitReached, isAuthFailure = false)
        }

        val result = accountStore.addOrUpdateAccount(
            countryCode = countryCode,
            olxUserId = profile.id,
            tokens = tokens,
            profile = profile.toSnapshot(),
            makeActive = true,
        )

        setActiveAccount(countryCode, result.account.localIndex)
        accountStore.clearAuthFailures(localIndex = null, countryCode = countryCode)
        authRepository.markAuthenticated()
        _user.value = profile

        analytics.logEvent(
            AnalyticsEvents.ACCOUNT_ADD_COMPLETED,
            mapOf(
                "country" to countryCode,
                "new_account_count" to accountCountFor(countryCode),
                "was_duplicate" to result.wasDuplicate,
            ),
        )
        analytics.setUserProperty(USER_PROPERTY_CONNECTED_ACCOUNT_COUNT, accountStore.recordFlow.value.accounts.size.toString())

        return if (result.wasDuplicate) {
            AddAccountOutcome.ReconnectedDuplicate(result.account)
        } else {
            AddAccountOutcome.Added(result.account)
        }
    }

    private suspend fun addAccountFailure(
        countryCode: String,
        reason: AddAccountFailureReason,
        isAuthFailure: Boolean = true,
    ): AddAccountOutcome.Failed {
        if (isAuthFailure) {
            accountStore.recordAuthFailure(localIndex = null, countryCode = countryCode)
        }
        analytics.logEvent(
            AnalyticsEvents.ACCOUNT_ADD_FAILED,
            mapOf(
                "country" to countryCode,
                "reason" to reason.analyticsReason,
                "consecutive_failures" to accountStore.consecutiveFailureCount(localIndex = null, countryCode = countryCode),
            ),
        )
        return AddAccountOutcome.Failed(reason)
    }

    /**
     * Back-compat surface for the pre-multi-account call sites (first-connect from the guest/
     * landing flow, and Profile's existing login action): completes authorization via [addAccount]
     * and collapses the result to the single-user shape those call sites already understand. New
     * multi-account UI (add/reconnect flows that need duplicate or cap messaging) should call
     * [addAccount] directly for the full [AddAccountOutcome].
     */
    suspend fun completeAuthorization(callbackUrl: String): Result<OlxUser> = runCatching {
        when (val outcome = addAccount(callbackUrl)) {
            is AddAccountOutcome.Added, is AddAccountOutcome.ReconnectedDuplicate ->
                _user.value ?: throw IllegalStateException("OLX account connected, but profile data is unavailable.")
            is AddAccountOutcome.Failed -> throw outcome.reason.toThrowable()
        }
    }

    suspend fun refreshProfile(): Result<OlxUser?> = runCatching {
        val session = authRepository.currentSession()
        if (!session.isAuthorized) {
            _user.value = null
            return@runCatching null
        }

        try {
            olxApiClient.getAuthenticatedUser().also { user ->
                _user.value = user
                backfillActiveAccountIdentityIfMissing(user)
            }
        } catch (error: Throwable) {
            _user.value = null
            throw error
        }
    }

    /**
     * The migrated pre-SIR-83 account is stored with `olxUserId = null` (never recorded before
     * this feature) - this fills it in from the first successful `users/me` after migration.
     * Without it, addOrUpdateAccount's dedupe can never match this account against a later
     * add-account attempt that resolves to the same OLX user (e.g. a force-relogin that didn't
     * actually force a fresh login), so it creates a genuine duplicate instead of recognising it.
     */
    private suspend fun backfillActiveAccountIdentityIfMissing(user: OlxUser) {
        val countryCode = olxCountryStore.current.code
        val active = activeAccountSnapshot(countryCode)
        if (active != null && active.olxUserId == null) {
            accountStore.backfillIdentityIfMissing(active.localIndex, user.id, user.toSnapshot())
        }
    }

    /**
     * Disconnects one account. If it was the active one for its country, [OlxAccountStore]
     * already promotes the most-recently-used remaining account (or clears the active pointer if
     * none remain) - this only needs to run the same client-side cache-clear/epoch-bump the store
     * itself has no way to trigger, and fall back to a full [OlxAuthRepository.logout] if nothing
     * remains active for that country.
     */
    suspend fun disconnectAccount(countryCode: String, localIndex: Int) {
        val before = accountStore.recordFlow.value
        val wasActive = before.activeByCountry[countryCode] == localIndex

        accountStore.disconnect(localIndex)

        val after = accountStore.recordFlow.value
        if (wasActive) {
            clearActiveAccountClientState(countryCode)
            if (after.activeByCountry[countryCode] == null) {
                authRepository.logout()
            }
        }

        analytics.logEvent(
            AnalyticsEvents.ACCOUNT_DISCONNECTED,
            mapOf("remaining_account_count" to after.accounts.size, "was_active" to wasActive),
        )
        analytics.setUserProperty(USER_PROPERTY_CONNECTED_ACCOUNT_COUNT, after.accounts.size.toString())
    }

    /**
     * Disconnects the active account for the current country (today's only shipped "Logout"
     * entry point). With no multi-account switcher live yet this is behaviorally identical to the
     * old single-account logout; once one ships, this promotes another stored account to active
     * rather than wiping every connected account, which is the more correct default. Whether the
     * Profile "Logout" action should instead mean "disconnect every account" is a product/UI
     * decision for a follow-up agent, not locked in here.
     */
    suspend fun logout() {
        val countryCode = olxCountryStore.current.code
        val activeIndex = accountStore.recordFlow.value.activeByCountry[countryCode]
        if (activeIndex != null) {
            disconnectAccount(countryCode, activeIndex)
        } else {
            authRepository.logout()
        }
        _user.value = null
    }

    suspend fun deleteSellSnapAccountData() {
        val countryCode = olxCountryStore.current.code
        authRepository.logout()
        accountStore.clearAll()
        clearActiveAccountClientState(countryCode)
        locationRepository.clearSavedLocation()
        draftMediaFileStore.deleteAll()
        advertOutcomeStore.clearAll()
        olxCountryStore.clear()
        analyticsConsentRepository.resetConsent()
        analytics.setUserProperty(USER_PROPERTY_CONNECTED_ACCOUNT_COUNT, "0")
    }

    suspend fun savedLocation(): OlxLocation? = locationRepository.getSavedLocation()

    suspend fun refreshLocationFromDevice(): OlxLocation? =
        locationRepository.fetchUserLocation()

    /**
     * Usable access token for one stored account (My Ads pager, SIR-87): refreshes + persists
     * first when [forceRefresh] or when the cached token is within [TOKEN_REFRESH_SKEW_SECONDS]
     * of expiry, otherwise returns the cached token with no network call. Returns null if the
     * account is gone or already [OlxAccountState.NeedsReconnect] - the caller (My Ads) maps that
     * to its per-page reconnect state rather than a generic load error. A terminal refresh
     * failure marks only this account, via [refreshAccountTokens].
     */
    internal suspend fun accessTokenFor(localIndex: Int, forceRefresh: Boolean = false): String? {
        val account = accountStore.recordFlow.value.accounts.find { it.localIndex == localIndex } ?: return null
        if (account.state == OlxAccountState.NeedsReconnect) return null

        if (!forceRefresh && !account.tokens.isExpired(nowEpochSeconds(), TOKEN_REFRESH_SKEW_SECONDS)) {
            return account.tokens.accessToken
        }

        return refreshAccountTokens(account)?.accessToken
    }

    /**
     * Keep-alive sweep (SIR-83 addition A1): refreshes every [OlxAccountState.Usable] account,
     * across all countries, whose token has not been refreshed in [KEEP_ALIVE_STALE_SECONDS] -
     * intended to be called on app foreground/cold start. `NeedsReconnect` accounts are skipped -
     * there is nothing to keep alive. A single account's failure never aborts the sweep.
     */
    suspend fun runKeepAliveRefresh() {
        val now = nowEpochSeconds()
        val staleAccounts = accountStore.recordFlow.value.accounts.filter {
            it.state == OlxAccountState.Usable && now - it.lastRefreshedAtEpochSeconds > KEEP_ALIVE_STALE_SECONDS
        }

        for (account in staleAccounts) {
            refreshAccountTokens(account)
        }
    }

    /**
     * Single refresh recipe shared by [runKeepAliveRefresh] and [accessTokenFor] so they can't
     * drift apart. Runs directly against the account's own country (not through the shared
     * authorized client, which is bound to whatever country is globally "current" and may not be
     * this account's), persists success via [OlxAccountStore.updateTokens] (never touching
     * `lastUsedAtEpochSeconds` - a refresh alone is not seller activity), and on a terminal
     * `invalid_grant`/`invalid_token` failure marks the account [OlxAccountState.NeedsReconnect].
     * Returns null on any failure, transient or terminal.
     */
    private suspend fun refreshAccountTokens(account: OlxAccountRecord): OlxTokens? {
        val country = OlxCountry.fromCode(account.countryCode) ?: return null
        val now = nowEpochSeconds()
        return runCatching {
            refreshOlxTokens(
                client = unauthenticatedHttpClient,
                tokenEndpointUrl = "https://www.${country.domain}/api/${OlxConfig.authTokenPath}",
                clientId = country.clientId,
                clientSecret = country.clientSecret,
                refreshToken = account.tokens.refreshToken,
                errorParser = errorParser,
            )
        }.onSuccess { refreshedTokens ->
            if (refreshedTokens != null) {
                accountStore.updateTokens(account.localIndex, refreshedTokens, now, updateLastUsed = false)
            }
        }.onFailure { error ->
            val olxError = (error as? OlxApiException)?.error
            if (olxError is OlxApiError.InvalidGrant || olxError is OlxApiError.InvalidToken) {
                accountStore.markNeedsReconnect(account.localIndex)
                analytics.logEvent(
                    AnalyticsEvents.ACCOUNT_TOKEN_EXPIRED_UNUSED,
                    mapOf("days_since_last_use" to (now - account.lastUsedAtEpochSeconds) / SECONDS_PER_DAY),
                )
            }
            // Any other error (network blip, 5xx, ...) is transient - leave the account Usable
            // and let the next caller (keep-alive sweep or another accessTokenFor) retry it.
        }.getOrNull()
    }

    private fun accountCountFor(countryCode: String): Int =
        accountStore.recordFlow.value.accounts.count { it.countryCode == countryCode }

    /** Clears the shared client's cached bearer token and bumps [switchEpoch], then repopulates
     * [user] from whichever account is now active for [countryCode] using its cached profile
     * snapshot - no network call, per PRD U4. Falls back to `null` only if no account is active
     * (e.g. the last account for this country was just disconnected) or the active account has no
     * cached profile yet (the freshly-migrated account, before its first `users/me`). */
    private fun clearActiveAccountClientState(countryCode: String) {
        authorizedHttpClient.authProvider<BearerAuthProvider>()?.clearToken()
        _switchEpoch.value += 1
        _user.value = activeAccountSnapshot(countryCode)?.toOlxUserOrNull()
    }

    private fun nowEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

    private companion object {
        const val MAX_ACCOUNTS_PER_COUNTRY = 3
        const val SECONDS_PER_DAY = 86_400L
        const val KEEP_ALIVE_STALE_SECONDS = 20 * SECONDS_PER_DAY
        const val TOKEN_REFRESH_SKEW_SECONDS = 60L
        const val USER_PROPERTY_CONNECTED_ACCOUNT_COUNT = "connected_account_count"
    }
}

/** Outcome of [SellerAccountRepository.addAccount]. `internal` because [Added]/[ReconnectedDuplicate]
 * carry the `internal` [OlxAccountRecord] - see the Kotlin visibility note in the SIR-83 task brief. */
internal sealed interface AddAccountOutcome {
    data class Added(val account: OlxAccountRecord) : AddAccountOutcome
    data class ReconnectedDuplicate(val account: OlxAccountRecord) : AddAccountOutcome
    data class Failed(val reason: AddAccountFailureReason) : AddAccountOutcome
}

sealed interface AddAccountFailureReason {
    data object AccountLimitReached : AddAccountFailureReason
    data class Authorization(val error: OlxApiError) : AddAccountFailureReason
}

private val AddAccountFailureReason.analyticsReason: String
    get() = when (this) {
        AddAccountFailureReason.AccountLimitReached -> "account_limit_reached"
        is AddAccountFailureReason.Authorization -> when (error) {
            is OlxApiError.MissingCode -> "missing_code"
            is OlxApiError.InvalidState -> "invalid_state"
            is OlxApiError.InvalidClient -> "invalid_client"
            is OlxApiError.InvalidGrant -> "invalid_grant"
            is OlxApiError.InvalidToken -> "invalid_token"
            is OlxApiError.InsufficientScope -> "insufficient_scope"
            is OlxApiError.NetworkFailure -> "network_failure"
            is OlxApiError.RateLimited -> "rate_limited"
            is OlxApiError.ValidationError -> "validation_error"
            is OlxApiError.Unknown -> "unknown"
        }
    }

private fun AddAccountFailureReason.toThrowable(): Throwable = when (this) {
    is AddAccountFailureReason.Authorization -> OlxApiException(error)
    AddAccountFailureReason.AccountLimitReached ->
        IllegalStateException("Maximum number of connected OLX accounts reached for this country.")
}

private fun Throwable.toOlxApiError(): OlxApiError =
    (this as? OlxApiException)?.error ?: OlxApiError.Unknown(message ?: "OLX authorization failed.")

private fun OlxUser.toSnapshot(): OlxProfileSnapshot = OlxProfileSnapshot(
    name = name,
    email = email,
    avatarUrl = avatar,
    isBusiness = isBusiness,
    phone = phone,
    status = status,
    createdAt = createdAt,
    lastLoginAt = lastLoginAt,
)

/** Reconstructs the full [OlxUser] shape from a cached account entry with no network call.
 * `null` only for the migrated legacy account before its first successful `users/me`, where
 * [OlxAccountRecord.olxUserId]/[OlxAccountRecord.profile] are not yet known. */
private fun OlxAccountRecord.toOlxUserOrNull(): OlxUser? {
    val id = olxUserId ?: return null
    val snapshot = profile ?: return null
    return OlxUser(
        id = id,
        email = snapshot.email.orEmpty(),
        status = snapshot.status,
        name = snapshot.name,
        phone = snapshot.phone,
        createdAt = snapshot.createdAt,
        lastLoginAt = snapshot.lastLoginAt,
        avatar = snapshot.avatarUrl,
        isBusiness = snapshot.isBusiness,
    )
}
