package com.sirelon.sellsnap.features.seller.auth.data

import kotlin.time.Clock

/**
 * One-time migration from the single-account [OlxTokenStore] to the multi-account
 * [OlxAccountStore] (SIR-83 foundation). Must run once, very early at startup - after
 * [OlxCountryStore.loadFromStorage] (it needs the current country to key the migrated account
 * to) and before anything else reads accounts from [OlxAccountStore].
 *
 * Idempotent: safe to call multiple times. Never throws on an unreadable/corrupt legacy blob -
 * falls back to an empty [OlxAccountsRecord], the same "disconnected" state a terminal
 * refresh failure produces today.
 */
class OlxAccountMigration internal constructor(
    private val accountStore: OlxAccountStore,
    private val legacyTokenStore: OlxTokenStore,
    private val countryStore: OlxCountryStore,
) {
    suspend fun migrateIfNeeded() {
        if (accountStore.readRaw() == null) {
            val legacy = runCatching { legacyTokenStore.read() }.getOrNull()
            val record = if (legacy == null) {
                OlxAccountsRecord()
            } else {
                val country = countryStore.current.code
                val now = nowEpochSeconds()
                OlxAccountsRecord(
                    accounts = listOf(
                        OlxAccountRecord(
                            localIndex = 1,
                            countryCode = country,
                            tokens = legacy,
                            lastUsedAtEpochSeconds = now,
                            lastRefreshedAtEpochSeconds = now,
                        ),
                    ),
                    activeByCountry = mapOf(country to 1),
                    nextLocalIndex = 2,
                )
            }
            accountStore.write(record)
            legacyTokenStore.clear()
        }
        // Always hydrate recordFlow: the branch above already wrote a fresh value, but the
        // already-migrated (early-return) path never populated it from storage otherwise.
        accountStore.loadFromStorage()
    }

    private fun nowEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000
}
