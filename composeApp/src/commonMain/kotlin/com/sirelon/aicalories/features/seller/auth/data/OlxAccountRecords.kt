package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import kotlinx.serialization.Serializable

// Multi-account foundation (SIR-83). Accounts are keyed by (countryCode, localIndex) rather
// than a single pointer so a later ticket (SIR-84, multi-country) can be added without a second
// migration. `localIndex` is stable and is NEVER reused after a disconnect - it is only ever
// assigned from `OlxAccountsRecord.nextLocalIndex`, which only ever increments.
enum class OlxAccountState { Usable, NeedsReconnect }

@Serializable
internal data class OlxProfileSnapshot(
    val name: String,
    val email: String?,
    val avatarUrl: String?,
    val isBusiness: Boolean,
    // Carried so a switch between cached accounts can repopulate the full OlxUser shape with
    // zero network calls (PRD U4: "switch completes with no network call when tokens are valid").
    val phone: String = "",
    val status: String = "",
    val createdAt: String = "",
    val lastLoginAt: String = "",
)

@Serializable
internal data class OlxAccountRecord(
    val localIndex: Int,
    val countryCode: String,
    val olxUserId: Long? = null,
    val tokens: OlxTokens,
    val profile: OlxProfileSnapshot? = null,
    val lastUsedAtEpochSeconds: Long,
    val lastRefreshedAtEpochSeconds: Long,
    val state: OlxAccountState = OlxAccountState.Usable,
    val consecutiveAuthFailures: Int = 0,
    val lastAuthFailureAtEpochSeconds: Long? = null,
)

@Serializable
internal data class OlxAccountsRecord(
    val schemaVersion: Int = 1,
    val accounts: List<OlxAccountRecord> = emptyList(),
    val activeByCountry: Map<String, Int> = emptyMap(),
    val nextLocalIndex: Int = 1,
    // Country-keyed pending-auth cooldown/failure state for a first-connect or add-account
    // attempt where no account exists yet. Reconnect on an existing account uses the
    // consecutiveAuthFailures/lastAuthFailureAtEpochSeconds fields on OlxAccountRecord above.
    val pendingAuthFailuresByCountry: Map<String, Int> = emptyMap(),
    val lastPendingAuthFailureAtByCountry: Map<String, Long> = emptyMap(),
)
