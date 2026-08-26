package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.datastore.createKeyValueStore
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock

// Result of addOrUpdateAccount: lets the caller tell "was this a duplicate?" (an existing
// account for the same olxUserId + country was refreshed in place) vs a brand-new account,
// so the seller can be messaged precisely per the PRD's "already-connected account" requirement.
internal data class OlxAddOrUpdateResult(
    val account: OlxAccountRecord,
    val wasDuplicate: Boolean,
)

/**
 * Multi-account store for OLX credentials (SIR-83 foundation). Backed by a single JSON blob
 * (`OlxAccountsRecord`) under key "accounts" in the "olx_accounts" KeyValueStore.
 *
 * Every mutation goes through [mutex]: read-modify-write, never a bare write, because two
 * concurrent refreshes on different accounts must not clobber each other - OLX rotates refresh
 * tokens daily, so a clobbered entry is dead, not merely stale.
 *
 * Cap enforcement (max accounts per country) is NOT this class's job - that's a policy decision
 * for the repository layer that consumes this store.
 */
internal class OlxAccountStore internal constructor(
    private val storage: KeyValueStore,
    private val json: Json,
) {
    constructor(json: Json) : this(createKeyValueStore("olx_accounts"), json)

    private val mutex = Mutex()
    private val _recordFlow = MutableStateFlow(OlxAccountsRecord())
    val recordFlow: StateFlow<OlxAccountsRecord> = _recordFlow.asStateFlow()

    /** Raw read for the migration idempotency check - null only if the key has never been written. */
    suspend fun readRaw(): OlxAccountsRecord? = mutex.withLock { readLocked() }

    /** Hydrates [recordFlow] from persisted storage (falls back to an empty record). */
    suspend fun loadFromStorage() {
        mutex.withLock { _recordFlow.value = readLocked() ?: OlxAccountsRecord() }
    }

    suspend fun write(record: OlxAccountsRecord) {
        mutex.withLock { writeLocked(record) }
    }

    suspend fun addOrUpdateAccount(
        countryCode: String,
        olxUserId: Long?,
        tokens: OlxTokens,
        profile: OlxProfileSnapshot?,
        makeActive: Boolean,
    ): OlxAddOrUpdateResult = mutateWithResult { current ->
        val now = nowEpochSeconds()
        // Primary match: same OLX user id. Fallback: an account in this country that has NO id
        // yet (the migrated pre-SIR-83 account, before its first successful users/me) but the
        // same email - without this, a broken add-account flow that returns the already-connected
        // user creates a genuine duplicate instead of refreshing the existing entry, because the
        // id-only match can never see past a still-null olxUserId.
        val existing = olxUserId?.let { userId ->
            current.accounts.find { it.countryCode == countryCode && it.olxUserId == userId }
        } ?: profile?.email?.takeIf { it.isNotBlank() }?.let { email ->
            current.accounts.find {
                it.countryCode == countryCode && it.olxUserId == null && it.profile?.email == email
            }
        }

        if (existing != null) {
            val updatedAccount = existing.copy(
                // The email-fallback match can find an account whose olxUserId is still null;
                // record the now-known id so a THIRD add attempt matches it by id directly too.
                olxUserId = olxUserId ?: existing.olxUserId,
                tokens = tokens,
                profile = profile ?: existing.profile,
                lastUsedAtEpochSeconds = now,
                lastRefreshedAtEpochSeconds = now,
                state = OlxAccountState.Usable,
                consecutiveAuthFailures = 0,
                lastAuthFailureAtEpochSeconds = null,
            )
            val updatedRecord = current.copy(
                accounts = current.accounts.map { if (it.localIndex == updatedAccount.localIndex) updatedAccount else it },
                activeByCountry = if (makeActive) {
                    current.activeByCountry + (countryCode to updatedAccount.localIndex)
                } else {
                    current.activeByCountry
                },
            )
            updatedRecord to OlxAddOrUpdateResult(updatedAccount, wasDuplicate = true)
        } else {
            val newAccount = OlxAccountRecord(
                localIndex = current.nextLocalIndex,
                countryCode = countryCode,
                olxUserId = olxUserId,
                tokens = tokens,
                profile = profile,
                lastUsedAtEpochSeconds = now,
                lastRefreshedAtEpochSeconds = now,
            )
            val updatedRecord = current.copy(
                accounts = current.accounts + newAccount,
                nextLocalIndex = current.nextLocalIndex + 1,
                activeByCountry = if (makeActive) {
                    current.activeByCountry + (countryCode to newAccount.localIndex)
                } else {
                    current.activeByCountry
                },
            )
            updatedRecord to OlxAddOrUpdateResult(newAccount, wasDuplicate = false)
        }
    }

    /**
     * Backfills the identity of an account whose [OlxAccountRecord.olxUserId] is still null - the
     * migrated pre-SIR-83 account never had one, and it's set here on its first successful
     * `users/me` after migration (see [SellerAccountRepository]). Without this, [addOrUpdateAccount]'s
     * dedupe (keyed on `olxUserId`) can never match that account against a later add-account
     * attempt that resolves to the SAME OLX user, so a broken force-relogin (returning the
     * already-connected account) creates a genuine duplicate entry instead of being recognised as
     * one. No-ops if the account already has an id (never overwrites a real id) or doesn't exist.
     */
    suspend fun backfillIdentityIfMissing(localIndex: Int, olxUserId: Long, profile: OlxProfileSnapshot) {
        mutate { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.localIndex == localIndex && it.olxUserId == null) {
                        it.copy(olxUserId = olxUserId, profile = profile)
                    } else {
                        it
                    }
                },
            )
        }
    }

    suspend fun setActive(countryCode: String, localIndex: Int) {
        mutate { current ->
            if (current.accounts.none { it.countryCode == countryCode && it.localIndex == localIndex }) {
                return@mutate current
            }
            current.copy(activeByCountry = current.activeByCountry + (countryCode to localIndex))
        }
    }

    /**
     * [updateLastUsed] must be false for a background refresh the seller didn't initiate (the
     * keep-alive sweep) - otherwise it looks like genuine activity, which caps
     * `account_token_expired_unused`'s `days_since_last_use` at roughly the keep-alive staleness
     * threshold (the exact number that event exists to measure) and skews [disconnect]'s
     * most-recently-used promotion toward accounts the seller never actually touched. The
     * reactive 401 refresh on the authorized client (an actual seller-triggered request) is
     * genuine use, so it keeps the default.
     */
    suspend fun updateTokens(
        localIndex: Int,
        tokens: OlxTokens,
        lastRefreshedAtEpochSeconds: Long,
        updateLastUsed: Boolean = true,
    ) {
        mutate { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.localIndex == localIndex) {
                        it.copy(
                            tokens = tokens,
                            lastRefreshedAtEpochSeconds = lastRefreshedAtEpochSeconds,
                            lastUsedAtEpochSeconds = if (updateLastUsed) lastRefreshedAtEpochSeconds else it.lastUsedAtEpochSeconds,
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    suspend fun markNeedsReconnect(localIndex: Int) {
        mutate { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.localIndex == localIndex) it.copy(state = OlxAccountState.NeedsReconnect) else it
                },
            )
        }
    }

    /** Call on a successful reconnect. */
    suspend fun markUsable(localIndex: Int) {
        mutate { current ->
            current.copy(
                accounts = current.accounts.map {
                    if (it.localIndex == localIndex) {
                        it.copy(
                            state = OlxAccountState.Usable,
                            consecutiveAuthFailures = 0,
                            lastAuthFailureAtEpochSeconds = null,
                        )
                    } else {
                        it
                    }
                },
            )
        }
    }

    /**
     * Removes the account. If it was the active one for its country, the most-recently-used
     * remaining account in that country becomes active; if none remain, the active pointer for
     * that country is removed entirely.
     */
    suspend fun disconnect(localIndex: Int) {
        mutate { current ->
            val target = current.accounts.find { it.localIndex == localIndex } ?: return@mutate current
            val remainingAccounts = current.accounts.filterNot { it.localIndex == localIndex }
            val wasActive = current.activeByCountry[target.countryCode] == localIndex
            val updatedActiveByCountry = if (!wasActive) {
                current.activeByCountry
            } else {
                val replacement = remainingAccounts
                    .filter { it.countryCode == target.countryCode }
                    .maxByOrNull { it.lastUsedAtEpochSeconds }
                if (replacement != null) {
                    current.activeByCountry + (target.countryCode to replacement.localIndex)
                } else {
                    current.activeByCountry - target.countryCode
                }
            }
            current.copy(accounts = remainingAccounts, activeByCountry = updatedActiveByCountry)
        }
    }

    /**
     * Bumps the failure counter/timestamp for the given key: the account (reconnect) when
     * [localIndex] is set, or the country's pending-connect bucket when it is null (first-connect
     * or add-account, before any account exists yet).
     */
    suspend fun recordAuthFailure(localIndex: Int?, countryCode: String) {
        mutate { current ->
            val now = nowEpochSeconds()
            if (localIndex != null) {
                current.copy(
                    accounts = current.accounts.map {
                        if (it.localIndex == localIndex) {
                            it.copy(
                                consecutiveAuthFailures = it.consecutiveAuthFailures + 1,
                                lastAuthFailureAtEpochSeconds = now,
                            )
                        } else {
                            it
                        }
                    },
                )
            } else {
                current.copy(
                    pendingAuthFailuresByCountry = current.pendingAuthFailuresByCountry +
                        (countryCode to ((current.pendingAuthFailuresByCountry[countryCode] ?: 0) + 1)),
                    lastPendingAuthFailureAtByCountry = current.lastPendingAuthFailureAtByCountry + (countryCode to now),
                )
            }
        }
    }

    /** Call on a successful authorization. */
    suspend fun clearAuthFailures(localIndex: Int?, countryCode: String) {
        mutate { current ->
            if (localIndex != null) {
                current.copy(
                    accounts = current.accounts.map {
                        if (it.localIndex == localIndex) {
                            it.copy(consecutiveAuthFailures = 0, lastAuthFailureAtEpochSeconds = null)
                        } else {
                            it
                        }
                    },
                )
            } else {
                current.copy(
                    pendingAuthFailuresByCountry = current.pendingAuthFailuresByCountry - countryCode,
                    lastPendingAuthFailureAtByCountry = current.lastPendingAuthFailureAtByCountry - countryCode,
                )
            }
        }
    }

    /** Pure read of the consecutive-failure count for a given key - no suspend needed. */
    fun consecutiveFailureCount(localIndex: Int?, countryCode: String): Int {
        val record = _recordFlow.value
        return if (localIndex != null) {
            record.accounts.find { it.localIndex == localIndex }?.consecutiveAuthFailures ?: 0
        } else {
            record.pendingAuthFailuresByCountry[countryCode] ?: 0
        }
    }

    /** Pure read of remaining cooldown seconds for a given key - no suspend needed. */
    fun remainingCooldownSeconds(
        localIndex: Int?,
        countryCode: String,
        nowEpochSeconds: Long,
        cooldownSeconds: Long = DEFAULT_AUTH_FAILURE_COOLDOWN_SECONDS,
    ): Long {
        val record = _recordFlow.value
        val lastFailureAt = if (localIndex != null) {
            record.accounts.find { it.localIndex == localIndex }?.lastAuthFailureAtEpochSeconds
        } else {
            record.lastPendingAuthFailureAtByCountry[countryCode]
        } ?: return 0L
        val elapsed = nowEpochSeconds - lastFailureAt
        return (cooldownSeconds - elapsed).coerceAtLeast(0L)
    }

    /** Wipes the whole store. Used by "delete my SellSnap data". */
    suspend fun clearAll() {
        mutex.withLock {
            storage.remove(KEY)
            _recordFlow.value = OlxAccountsRecord()
        }
    }

    private suspend fun readLocked(): OlxAccountsRecord? =
        storage.getString(KEY)?.let { raw -> runCatching { json.decodeFromString<OlxAccountsRecord>(raw) }.getOrNull() }

    private suspend fun writeLocked(record: OlxAccountsRecord) {
        storage.putString(KEY, json.encodeToString<OlxAccountsRecord>(record))
        _recordFlow.value = record
    }

    private suspend fun mutate(block: (OlxAccountsRecord) -> OlxAccountsRecord): OlxAccountsRecord =
        mutateWithResult { current -> val updated = block(current); updated to updated }

    private suspend fun <T> mutateWithResult(block: (OlxAccountsRecord) -> Pair<OlxAccountsRecord, T>): T =
        mutex.withLock {
            val current = readLocked() ?: OlxAccountsRecord()
            val (updated, result) = block(current)
            writeLocked(updated)
            result
        }

    private fun nowEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

    private companion object {
        const val KEY = "accounts"
        const val DEFAULT_AUTH_FAILURE_COOLDOWN_SECONDS = 60L
    }
}
