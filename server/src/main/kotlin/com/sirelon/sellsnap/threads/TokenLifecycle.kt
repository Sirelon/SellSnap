package com.sirelon.sellsnap.threads

import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.store.SocialStore
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Result of `/token` intake. [summary] is for logs and tests; it never holds a token. */
sealed interface IntakeResult {
    val summary: String

    class Accepted(
        val accountId: String,
        val tokenExpiresAt: Instant,
        val permissionsExpireAt: Instant?,
        /** Expected scopes that are not granted yet; the owner is warned, the token is still stored. */
        val missingOptionalScopes: Set<String>,
    ) : IntakeResult {
        override val summary: String get() = "Token stored for $accountId"
    }

    class Rejected(val reason: Reason, override val summary: String) : IntakeResult

    enum class Reason { UnknownAccount, InvalidToken, MissingScopes, HandleMismatch, ThreadsError }
}

/** Result of a refresh, scheduled or forced. [summary] never holds a token. */
sealed interface RefreshResult {
    val summary: String

    class Refreshed(
        val tokenExpiresAt: Instant,
        val permissionsExpireAt: Instant?,
    ) : RefreshResult {
        override val summary: String get() = "Token refreshed"
    }

    class Failed(override val summary: String, val permanent: Boolean = false) : RefreshResult
}

/** Alerts raised by one account's token check. */
class TokenTickResult(val refresh: RefreshResult?, val alerts: List<AlertRequest>)

/**
 * Takes in the owner's Threads token and keeps it alive: exchange or refresh on intake, a refresh
 * every 50 days, and alert requests 10 days before the token or the private-profile permission ends.
 */
class TokenLifecycle(
    private val store: SocialStore,
    private val threads: ThreadsApi,
    private val clock: Clock,
    private val retryDelays: List<kotlin.time.Duration> = DEFAULT_RETRY_DELAYS,
) {
    /**
     * Validates [rawToken] for [accountId] and stores a long-lived token for it. The `/me` username
     * must equal the account handle; nothing is stored when any step fails.
     */
    suspend fun intake(accountId: String, rawToken: String): IntakeResult {
        val account = store.account(accountId)
            ?: return rejected(IntakeResult.Reason.UnknownAccount, "No account named $accountId")
        val now = clock.instant()
        try {
            val info = call { threads.debugToken(rawToken) }
            if (!info.isValid) return rejected(IntakeResult.Reason.InvalidToken, "Threads reports the token as not valid")
            val missing = REQUIRED_SCOPES - info.scopes
            if (missing.isNotEmpty()) {
                return rejected(IntakeResult.Reason.MissingScopes, "The token lacks the scopes ${missing.sorted().joinToString()}")
            }

            val shortLived = info.expiresAt != null && Duration.between(now, info.expiresAt) < SHORT_LIVED_BELOW
            val tooYoungToRefresh = info.issuedAt != null && Duration.between(info.issuedAt, now) < MIN_REFRESH_AGE
            val token: String
            val expiresAt: Instant
            val refreshedAt: Instant
            when {
                shortLived -> {
                    val issued = call { threads.exchangeToken(rawToken) }
                    token = issued.accessToken
                    expiresAt = now.plusSeconds(issued.expiresInSeconds)
                    refreshedAt = now
                }
                tooYoungToRefresh -> {
                    // A long-lived token younger than 24 hours cannot be refreshed; it is already long-lived.
                    token = rawToken
                    expiresAt = info.expiresAt ?: now.plus(LONG_LIVED)
                    refreshedAt = info.issuedAt
                }
                else -> {
                    val issued = call { threads.refreshToken(rawToken) }
                    token = issued.accessToken
                    expiresAt = now.plusSeconds(issued.expiresInSeconds)
                    refreshedAt = now
                }
            }

            val me = call { threads.me(token) }
            if (!sameHandle(me.username, account.handle)) {
                return rejected(
                    IntakeResult.Reason.HandleMismatch,
                    "The token belongs to @${redact(me.username)}, not to @${account.handle}",
                )
            }

            val permissionsExpireAt = if (token == rawToken) {
                info.dataAccessExpiresAt
            } else {
                readPermissionsExpiry(token, fallback = info.dataAccessExpiresAt)
            }
            store.transaction { tx ->
                val current = tx.account(accountId) ?: account
                tx.putAccount(
                    current.copy(
                        token = token,
                        tokenExpiresAt = expiresAt,
                        tokenRefreshedAt = refreshedAt,
                        threadsUserId = me.id,
                        permissionsExpireAt = permissionsExpireAt,
                    ),
                )
                tx.putEvent(SocialEvent("token_intake_${accountId}_${now.toEpochMilli()}", accountId, EventType.TokenRefresh, now))
            }
            return IntakeResult.Accepted(accountId, expiresAt, permissionsExpireAt, EXPECTED_SCOPES - info.scopes)
        } catch (failure: ThreadsFailure) {
            val reason = if (failure is ThreadsFailure.Permanent && failure.code == INVALID_TOKEN_CODE) {
                IntakeResult.Reason.InvalidToken
            } else {
                IntakeResult.Reason.ThreadsError
            }
            return rejected(reason, redact(failure.message.orEmpty()))
        }
    }

    /**
     * One account's token check for a tick: refresh when the last refresh is over 50 days old, then
     * request alerts for a token or a permission grant that ends within 10 days. Each alert type is
     * requested once per account and day.
     */
    suspend fun tick(accountId: String): TokenTickResult {
        var account = store.account(accountId) ?: return TokenTickResult(null, emptyList())
        if (account.token == null) return TokenTickResult(null, emptyList())
        val now = clock.instant()
        val alerts = mutableListOf<AlertRequest>()

        var refresh: RefreshResult? = null
        val refreshedAt = account.tokenRefreshedAt
        if (refreshedAt == null || Duration.between(refreshedAt, now) > REFRESH_AFTER) {
            refresh = refresh(account)
            if (refresh is RefreshResult.Failed && refresh.permanent) {
                alerts += AlertRequest(AlertType.TokenRefreshFailed, accountId, detail = refresh.summary)
            }
            account = store.account(accountId) ?: account
        }

        account.tokenExpiresAt?.takeIf { soon(it, now) }?.let {
            alerts += AlertRequest(AlertType.TokenExpiry, accountId, expiresAt = it)
        }
        account.permissionsExpireAt?.takeIf { soon(it, now) }?.let {
            alerts += AlertRequest(AlertType.PermissionsExpiry, accountId, expiresAt = it)
        }
        return TokenTickResult(refresh, alerts.filter { store.recordEvent(alertEvent(it, now)) })
    }

    /** Refreshes the token now, whatever its age; the `/refresh` command calls this. */
    suspend fun forceRefresh(accountId: String): RefreshResult {
        val account = store.account(accountId) ?: return RefreshResult.Failed("No account named $accountId")
        if (account.token == null) return RefreshResult.Failed("Account $accountId has no token")
        return refresh(account)
    }

    private suspend fun refresh(account: SocialAccount): RefreshResult {
        val now = clock.instant()
        try {
            val issued = call { threads.refreshToken(account.token.orEmpty()) }
            val expiresAt = now.plusSeconds(issued.expiresInSeconds)
            val permissionsExpireAt = readPermissionsExpiry(issued.accessToken, fallback = account.permissionsExpireAt)
            store.transaction { tx ->
                val current = tx.account(account.id) ?: account
                tx.putAccount(
                    current.copy(
                        token = issued.accessToken,
                        tokenExpiresAt = expiresAt,
                        tokenRefreshedAt = now,
                        permissionsExpireAt = permissionsExpireAt,
                    ),
                )
                tx.putEvent(SocialEvent("token_refresh_${account.id}_${now.toEpochMilli()}", account.id, EventType.TokenRefresh, now))
            }
            return RefreshResult.Refreshed(expiresAt, permissionsExpireAt)
        } catch (failure: ThreadsFailure) {
            return RefreshResult.Failed(redact(failure.message.orEmpty()), permanent = failure is ThreadsFailure.Permanent)
        }
    }

    /** Re-reads `data_access_expires_at` from [token]; keeps [fallback] when the read fails. */
    private suspend fun readPermissionsExpiry(token: String, fallback: Instant?): Instant? =
        try {
            call { threads.debugToken(token) }.dataAccessExpiresAt
        } catch (_: ThreadsFailure) {
            fallback
        }

    private fun alertEvent(alert: AlertRequest, now: Instant) = SocialEvent(
        id = alert.eventId(now),
        accountId = alert.accountId,
        type = EventType.Alert,
        at = now,
        detail = alert.type.key,
    )

    private fun soon(at: Instant, now: Instant): Boolean = !at.isAfter(now.plus(ALERT_WINDOW))

    private fun sameHandle(username: String, handle: String): Boolean =
        username.trimStart('@').equals(handle.trimStart('@'), ignoreCase = true)

    private fun rejected(reason: IntakeResult.Reason, summary: String) = IntakeResult.Rejected(reason, summary)

    private suspend fun <T> call(block: suspend (Int) -> T): T = withRetry(retryDelays, block)

    companion object {
        /** A token with less than this left is short-lived (they last an hour). */
        private val SHORT_LIVED_BELOW: Duration = Duration.ofHours(24)

        /** Threads refuses to refresh a long-lived token younger than this. */
        private val MIN_REFRESH_AGE: Duration = Duration.ofHours(24)
        private val LONG_LIVED: Duration = Duration.ofDays(60)
        private val REFRESH_AFTER: Duration = Duration.ofDays(50)
        private val ALERT_WINDOW: Duration = Duration.ofDays(10)
        private const val INVALID_TOKEN_CODE = 190

        /** A token without these cannot do the job; intake refuses it. */
        val REQUIRED_SCOPES = setOf("threads_basic", "threads_content_publish", "threads_delete")

        /** Scopes the Meta app requests for later phases; a missing one only draws a warning. */
        val EXPECTED_SCOPES = setOf(
            "threads_read_replies",
            "threads_manage_replies",
            "threads_manage_insights",
            "threads_keyword_search",
            "threads_manage_mentions",
        )
    }
}
