package com.sirelon.sellsnap.threads

import com.sirelon.sellsnap.fakes.FakeThreadsApi
import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.SocialAccount
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenLifecycleTest {
    private val clock = MutableClock(NOW)
    private val store = InMemorySocialStore()
    private val threads = FakeThreadsApi(store)
    private val lifecycle = TokenLifecycle(store, threads, clock)

    init {
        store.put(testAccount().copy(handle = "sellsnap.app"))
    }

    private fun account(): SocialAccount = store.allAccounts.getValue("staging")

    private fun tokenInfo(
        expiresIn: Duration? = Duration.ofHours(1),
        issuedAgo: Duration? = Duration.ofMinutes(1),
        dataAccessIn: Duration? = Duration.ofDays(90),
        scopes: Set<String> = FakeThreadsApi().tokenInfo.scopes,
        isValid: Boolean = true,
    ) = TokenInfo(
        isValid = isValid,
        scopes = scopes,
        userId = "threads-user",
        expiresAt = expiresIn?.let { NOW + it },
        dataAccessExpiresAt = dataAccessIn?.let { NOW + it },
        issuedAt = issuedAgo?.let { NOW - it },
    )

    private fun withToken(
        refreshedAgo: Duration? = null,
        expiresIn: Duration? = null,
        permissionsIn: Duration? = null,
    ) = store.put(
        account().copy(
            token = "stored-token",
            threadsUserId = "threads-user",
            tokenRefreshedAt = refreshedAgo?.let { NOW - it },
            tokenExpiresAt = expiresIn?.let { NOW + it },
            permissionsExpireAt = permissionsIn?.let { NOW + it },
        ),
    )

    // Intake

    @Test
    fun aShortLivedTokenIsExchangedAndStored() = runTest {
        threads.tokenInfo = tokenInfo(expiresIn = Duration.ofHours(1))

        val result = lifecycle.intake("staging", RAW)

        val accepted = assertIs<IntakeResult.Accepted>(result)
        assertEquals(1, threads.callsTo("exchangeToken").size)
        assertTrue(threads.callsTo("refreshToken").isEmpty())
        val saved = account()
        assertEquals("long-lived-token", saved.token)
        assertEquals(NOW.plus(Duration.ofDays(60)), saved.tokenExpiresAt)
        assertEquals(NOW, saved.tokenRefreshedAt)
        assertEquals("threads-user", saved.threadsUserId)
        assertEquals(NOW + Duration.ofDays(90), saved.permissionsExpireAt)
        assertEquals(saved.tokenExpiresAt, accepted.tokenExpiresAt)
        assertEquals(
            setOf("threads_read_replies", "threads_manage_replies", "threads_manage_insights", "threads_keyword_search", "threads_manage_mentions"),
            accepted.missingOptionalScopes,
        )
    }

    @Test
    fun aLongLivedTokenIsRefreshedNotExchanged() = runTest {
        threads.tokenInfo = tokenInfo(expiresIn = Duration.ofDays(55), issuedAgo = Duration.ofDays(5))

        assertIs<IntakeResult.Accepted>(lifecycle.intake("staging", RAW))

        assertTrue(threads.callsTo("exchangeToken").isEmpty())
        assertEquals(1, threads.callsTo("refreshToken").size)
        assertEquals("refreshed-token", account().token)
        assertEquals(NOW, account().tokenRefreshedAt)
    }

    @Test
    fun aLongLivedTokenUnderADayOldIsStoredAsItIs() = runTest {
        threads.tokenInfo = tokenInfo(expiresIn = Duration.ofDays(60), issuedAgo = Duration.ofHours(2))

        assertIs<IntakeResult.Accepted>(lifecycle.intake("staging", RAW))

        assertTrue(threads.callsTo("exchangeToken").isEmpty())
        assertTrue(threads.callsTo("refreshToken").isEmpty())
        assertEquals(RAW, account().token)
        assertEquals(NOW - Duration.ofHours(2), account().tokenRefreshedAt)
        assertEquals(NOW + Duration.ofDays(60), account().tokenExpiresAt)
    }

    @Test
    fun noPermissionExpiryIsStoredAsNull() = runTest {
        threads.tokenInfo = tokenInfo(dataAccessIn = null)

        assertIs<IntakeResult.Accepted>(lifecycle.intake("staging", RAW))

        assertNull(account().permissionsExpireAt)
    }

    @Test
    fun anInvalidTokenStoresNothing() = runTest {
        threads.tokenInfo = tokenInfo(isValid = false)

        val result = lifecycle.intake("staging", RAW)

        assertEquals(IntakeResult.Reason.InvalidToken, assertIs<IntakeResult.Rejected>(result).reason)
        assertNoChange()
    }

    @Test
    fun aTokenThatThreadsRejectsWith190IsInvalid() = runTest {
        threads.failNext("debugToken", ThreadsFailure.Permanent(190, "Threads rejected the request (HTTP 400, code 190)"))

        val result = lifecycle.intake("staging", RAW)

        assertEquals(IntakeResult.Reason.InvalidToken, assertIs<IntakeResult.Rejected>(result).reason)
        assertNoChange()
    }

    @Test
    fun aTransientThreadsFailureIsAThreadsError() = runTest {
        threads.failNext("debugToken", *Array(4) { ThreadsFailure.Transient("Threads server error (HTTP 503)") })

        val result = lifecycle.intake("staging", RAW)

        assertEquals(IntakeResult.Reason.ThreadsError, assertIs<IntakeResult.Rejected>(result).reason)
        assertNoChange()
    }

    @Test
    fun aMissingRequiredScopeIsRefused() = runTest {
        threads.tokenInfo = tokenInfo(scopes = setOf("threads_basic", "threads_content_publish"))

        val result = assertIs<IntakeResult.Rejected>(lifecycle.intake("staging", RAW))

        assertEquals(IntakeResult.Reason.MissingScopes, result.reason)
        assertTrue("threads_delete" in result.summary)
        assertNoChange()
    }

    @Test
    fun aTokenOfAnotherAccountIsRefusedAndNothingIsStored() = runTest {
        threads.me = Me("other", "someone.else")

        val result = lifecycle.intake("staging", RAW)

        assertEquals(IntakeResult.Reason.HandleMismatch, assertIs<IntakeResult.Rejected>(result).reason)
        assertNoChange()
    }

    @Test
    fun theHandleComparisonIgnoresCaseAndAtSigns() = runTest {
        threads.me = Me("threads-user", "SellSnap.App")

        assertIs<IntakeResult.Accepted>(lifecycle.intake("staging", RAW))
    }

    @Test
    fun anUnknownAccountIsRefused() = runTest {
        val result = lifecycle.intake("nobody", RAW)

        assertEquals(IntakeResult.Reason.UnknownAccount, assertIs<IntakeResult.Rejected>(result).reason)
        assertTrue(threads.calls.isEmpty())
    }

    @Test
    fun noIntakeResultOrFailureEchoesTheToken() = runTest {
        val results = mutableListOf<IntakeResult>()
        results += lifecycle.intake("staging", RAW)
        threads.tokenInfo = tokenInfo(isValid = false)
        results += lifecycle.intake("staging", RAW)
        threads.tokenInfo = tokenInfo(scopes = emptySet())
        results += lifecycle.intake("staging", RAW)
        threads.tokenInfo = tokenInfo()
        threads.me = Me("x", "other")
        results += lifecycle.intake("staging", RAW)
        threads.failNext("debugToken", ThreadsFailure.Permanent(190, "bad access_token=$RAW"))
        results += lifecycle.intake("staging", RAW)
        results += lifecycle.intake("missing", RAW)

        for (result in results) {
            assertFalse(RAW in result.summary, result.summary)
            assertFalse(RAW in result.toString().replace(Regex("@[0-9a-f]+"), ""), result.toString())
        }
        assertFalse(RAW in store.events.toString())
    }

    private fun assertNoChange() {
        assertNull(account().token)
        assertNull(account().threadsUserId)
        assertTrue(store.events.isEmpty())
    }

    // Tick

    @Test
    fun aTokenRefreshedMoreThanFiftyOneDaysAgoIsRefreshed() = runTest {
        withToken(refreshedAgo = Duration.ofDays(51), expiresIn = Duration.ofDays(9 + 60 - 51 + 40))
        threads.tokenInfo = tokenInfo(dataAccessIn = Duration.ofDays(75))

        val result = lifecycle.tick("staging")

        assertIs<RefreshResult.Refreshed>(result.refresh)
        assertEquals(listOf("stored-token"), threads.callsTo("refreshToken").map { it.args[0] })
        val saved = account()
        assertEquals("refreshed-token", saved.token)
        assertEquals(NOW, saved.tokenRefreshedAt)
        assertEquals(NOW + Duration.ofDays(60), saved.tokenExpiresAt)
        assertEquals(NOW + Duration.ofDays(75), saved.permissionsExpireAt)
        assertEquals(1, store.events.values.count { it.type == EventType.TokenRefresh })
        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun aTokenRefreshedFortyNineDaysAgoIsLeftAlone() = runTest {
        withToken(refreshedAgo = Duration.ofDays(49), expiresIn = Duration.ofDays(11))

        val result = lifecycle.tick("staging")

        assertNull(result.refresh)
        assertTrue(threads.calls.isEmpty())
        assertEquals("stored-token", account().token)
    }

    @Test
    fun anAccountWithoutATokenIsSkipped() = runTest {
        val result = lifecycle.tick("staging")

        assertNull(result.refresh)
        assertTrue(result.alerts.isEmpty())
        assertTrue(threads.calls.isEmpty())
    }

    @Test
    fun aFailingRefreshWithTenDaysLeftAlertsOncePerDay() = runTest {
        withToken(refreshedAgo = Duration.ofDays(52), expiresIn = Duration.ofDays(8))
        threads.failNext("refreshToken", *Array(12) { ThreadsFailure.Transient("Threads server error (HTTP 503)") })

        val first = lifecycle.tick("staging")
        val sameDay = lifecycle.tick("staging")
        clock.advance(Duration.ofDays(1))
        val nextDay = lifecycle.tick("staging")

        assertIs<RefreshResult.Failed>(first.refresh)
        assertEquals(listOf(AlertType.TokenExpiry), first.alerts.map { it.type })
        assertEquals(NOW + Duration.ofDays(8), first.alerts.single().expiresAt)
        assertTrue(sameDay.alerts.isEmpty())
        assertEquals(listOf(AlertType.TokenExpiry), nextDay.alerts.map { it.type })
        assertEquals("stored-token", account().token)
        assertTrue("alert_token_expiry_staging_20261020" in store.events)
        assertTrue("alert_token_expiry_staging_20261021" in store.events)
    }

    @Test
    fun aPermissionGrantEndingWithinTenDaysAlerts() = runTest {
        withToken(refreshedAgo = Duration.ofDays(1), expiresIn = Duration.ofDays(59), permissionsIn = Duration.ofDays(9))

        val result = lifecycle.tick("staging")

        assertEquals(listOf(AlertType.PermissionsExpiry), result.alerts.map { it.type })
        assertTrue(threads.calls.isEmpty())
    }

    @Test
    fun aPermissionGrantElevenDaysAwayDoesNotAlert() = runTest {
        withToken(refreshedAgo = Duration.ofDays(1), expiresIn = Duration.ofDays(59), permissionsIn = Duration.ofDays(11))

        assertTrue(lifecycle.tick("staging").alerts.isEmpty())
    }

    @Test
    fun aPermanentRefreshFailureAlertsAtOnceAndKeepsTheStoredToken() = runTest {
        withToken(refreshedAgo = Duration.ofDays(52), expiresIn = Duration.ofDays(30))
        threads.failNext("refreshToken", ThreadsFailure.Permanent(190, "bad access_token=stored-token"))

        val result = lifecycle.tick("staging")

        val alert = result.alerts.single()
        assertEquals(AlertType.TokenRefreshFailed, alert.type)
        assertFalse("stored-token" in alert.detail.orEmpty())
        assertEquals("stored-token", account().token)
    }

    // Forced refresh

    @Test
    fun aForcedRefreshRunsWhateverTheTokenAge() = runTest {
        withToken(refreshedAgo = Duration.ofDays(2), expiresIn = Duration.ofDays(58))

        val result = lifecycle.forceRefresh("staging")

        assertIs<RefreshResult.Refreshed>(result)
        assertEquals("refreshed-token", account().token)
        assertEquals(NOW, account().tokenRefreshedAt)
    }

    @Test
    fun aFailedForcedRefreshReportsWithoutATokenAndStoresNothing() = runTest {
        withToken(refreshedAgo = Duration.ofHours(3), expiresIn = Duration.ofDays(60))
        threads.failNext("refreshToken", ThreadsFailure.Permanent(10, "Threads rejected the request (HTTP 400, code 10)"))

        val result = lifecycle.forceRefresh("staging")

        val failed = assertIs<RefreshResult.Failed>(result)
        assertTrue(failed.permanent)
        assertFalse("stored-token" in failed.summary)
        assertEquals("stored-token", account().token)
        assertIs<RefreshResult.Failed>(lifecycle.forceRefresh("nobody"))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-20T09:00:00Z")
        const val RAW = "RAW-TOKEN-xyz"
    }
}
