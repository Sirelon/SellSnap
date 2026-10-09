package com.sirelon.sellsnap.tick

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.insights.InsightsCollector
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.telegram.PreviewSender
import com.sirelon.sellsnap.telegram.Previews
import com.sirelon.sellsnap.threads.Publisher
import com.sirelon.sellsnap.threads.ThreadsFailure
import com.sirelon.sellsnap.threads.TokenLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class TickRunnerTest {
    private fun runner(fx: AgentFixture, store: SocialStore = fx.store, timeout: kotlin.time.Duration = 9.minutes): TickRunner {
        val base = fx.config.publicBaseUrl
        val previews = Previews(fx.images, base)
        val sender = PreviewSender(fx.telegram, previews, store, AgentFixture.OWNER)
        return TickRunner(
            store = store,
            tokens = TokenLifecycle(store, fx.threads, fx.clock),
            publisher = Publisher(store, fx.threads, fx.images, fx.clock, base),
            filler = QueueFiller(store, sender, previews, fx.clock),
            previewSender = sender,
            alerts = Alerts(store, fx.telegram, fx.clock, AgentFixture.OWNER),
            insights = InsightsCollector(store, fx.threads, fx.clock),
            clock = fx.clock,
            timeout = timeout,
        )
    }

    private class BrokenStore(private val delegate: SocialStore, private val brokenAccount: String) : SocialStore by delegate {
        override suspend fun postsOf(accountId: String): List<SocialPost> {
            if (accountId == brokenAccount) throw IllegalStateException("boom ?access_token=SECRET123")
            return delegate.postsOf(accountId)
        }
    }

    private class HangingStore(private val delegate: SocialStore) : SocialStore by delegate {
        override suspend fun accounts(): List<SocialAccount> {
            delay(Long.MAX_VALUE)
            return delegate.accounts()
        }
    }

    @Test
    fun anAccountThatThrowsIsReportedAndTheOthersStillRun() = runTest {
        val fx = AgentFixture(wrapStore = { BrokenStore(it, brokenAccount = "pl") })
        fx.memory.put(testAccount(id = "pl", codePrefix = "pl", language = "pl"))
        fx.memory.put(testAccount(id = "ua", codePrefix = "uk"))
        fx.put(testPost(id = "ukt0001", accountId = "ua", status = PostStatus.Drafted))

        val summary = runner(fx).run()

        assertEquals(2, summary.accounts)
        assertEquals(1, summary.failedAccounts)
        assertFalse(summary.ok)
        assertEquals(PostStatus.Pending, fx.status("ukt0001"))
        val error = fx.memory.events.values.single { it.type == EventType.Error }
        assertEquals("pl", error.accountId)
        assertFalse("SECRET123" in error.detail.orEmpty())
        val alert = fx.sentTexts().single { "pl" in it && "failed" in it }
        assertFalse("SECRET123" in alert)
        assertFalse("SECRET123" in fx.telegram.calls.joinToString { (it as? FakeTelegramApi.Call.Sent)?.text.orEmpty() })
    }

    @Test
    fun theFailureAlertIsSentOncePerDay() = runTest {
        val fx = AgentFixture(wrapStore = { BrokenStore(it, brokenAccount = "staging") })
        fx.memory.put(testAccount())
        val tick = runner(fx)
        tick.run()
        fx.clock.advance(Duration.ofMinutes(60))
        tick.run()
        assertEquals(1, fx.sentTexts().count { "The tick failed for staging" in it })
        assertEquals(2, fx.memory.events.values.count { it.type == EventType.Error })
    }

    @Test
    fun pausedAccountsAreSkipped() = runTest {
        val fx = AgentFixture()
        fx.memory.put(testAccount().copy(paused = true))
        fx.put(testPost(status = PostStatus.Drafted))
        val summary = runner(fx).run()
        assertEquals(0, summary.accounts)
        assertEquals(PostStatus.Drafted, fx.status("stt0001"))
    }

    @Test
    fun aRefreshedTokenIsTheOneUsedToPublish() = runTest {
        val fx = AgentFixture()
        fx.memory.put(
            fx.publishable(testAccount()).copy(tokenRefreshedAt = AgentFixture.NOW.minus(Duration.ofDays(51)), token = "old-token"),
        )
        fx.put(testPost(status = PostStatus.Approved, scheduledAt = AgentFixture.NOW.minusSeconds(60)))

        val summary = runner(fx).run()

        assertEquals(1, summary.published)
        val methods = fx.threads.calls.map { it.method }
        assertTrue(methods.indexOf("refreshToken") < methods.indexOf("createContainer"))
        assertEquals("refreshed-token", fx.threads.callsTo("createContainer").single().args[1])
    }

    @Test
    fun aPublishedPostGetsItsPreviewRefreshedWithDelete() = runTest {
        val fx = AgentFixture()
        fx.memory.put(fx.publishable(testAccount()))
        fx.put(
            testPost(status = PostStatus.Approved, scheduledAt = AgentFixture.NOW.minusSeconds(60)).copy(telegramMessageId = 900),
        )
        runner(fx).run()

        val edit = fx.telegram.callsOf<FakeTelegramApi.Call.Edited>().single()
        assertEquals(900L, edit.messageId)
        assertEquals(
            listOf("Delete"),
            edit.markup?.inlineKeyboard.orEmpty().flatten().map { it.text },
        )
    }

    @Test
    fun aPermanentPublishFailureAlertsOnceEvenWhenTickedAgain() = runTest {
        val fx = AgentFixture()
        fx.memory.put(fx.publishable(testAccount()))
        fx.put(testPost(status = PostStatus.Approved, scheduledAt = AgentFixture.NOW.minusSeconds(60)))
        fx.threads.failNext("createContainer", ThreadsFailure.Permanent(190, "Threads rejected the token (code 190)"))

        runner(fx).run()
        assertEquals(PostStatus.Failed, fx.status("stt0001"))
        assertTrue(fx.memory.allAccounts.getValue("staging").paused)
        assertEquals(1, fx.sentTexts().count { "/resume staging" in it })

        // Resume and fail again on the same day: the alert is deduplicated.
        fx.memory.put(fx.memory.allAccounts.getValue("staging").copy(paused = false))
        fx.put(testPost(id = "stt0002", status = PostStatus.Approved, scheduledAt = AgentFixture.NOW.minusSeconds(30)))
        fx.threads.failNext("createContainer", ThreadsFailure.Permanent(190, "Threads rejected the token (code 190)"))
        runner(fx).run()
        assertEquals(1, fx.sentTexts().count { "/resume staging" in it })
    }

    private fun livePost(id: String, accountId: String) = testPost(id = id, accountId = accountId, status = PostStatus.Published)
        .copy(mediaId = "media-$id", publishedAt = AgentFixture.NOW.minus(Duration.ofDays(2)))

    @Test
    fun snapshotsRunAfterPublishAndFill() = runTest {
        val fx = AgentFixture()
        fx.memory.put(fx.publishable(testAccount()))
        fx.put(testPost(status = PostStatus.Approved, scheduledAt = AgentFixture.NOW.minusSeconds(60)))

        val summary = runner(fx).run()

        val methods = fx.threads.calls.map { it.method }
        assertTrue(methods.indexOf("publish") < methods.indexOf("mediaInsights"))
        assertTrue(methods.indexOf("mediaInsights") < methods.indexOf("followersCount"))
        // The post that just went live has its first snapshot in the same tick.
        assertEquals(1, summary.snapshots)
        assertEquals(setOf("stt0001_20261012"), fx.memory.allMetrics.keys)
        assertEquals(setOf("staging_20261012"), fx.memory.allAccountMetrics.keys)
        assertTrue(summary.line().contains("snapshots=1"))
    }

    @Test
    fun aPausedAccountWithATokenGetsOnlyTheSnapshot() = runTest {
        val fx = AgentFixture()
        fx.memory.put(fx.publishable(testAccount()).copy(paused = true))
        fx.put(livePost("stt0001", "staging"))
        fx.put(testPost(id = "stt0002", status = PostStatus.Approved, scheduledAt = AgentFixture.NOW.minusSeconds(60)))
        fx.put(testPost(id = "stt0003", status = PostStatus.Drafted))

        val summary = runner(fx).run()

        assertEquals(0, summary.accounts)
        assertEquals(1, summary.snapshots)
        assertEquals(setOf("mediaInsights", "followersCount"), fx.threads.calls.map { it.method }.toSet())
        assertEquals(PostStatus.Approved, fx.status("stt0002"))
        assertEquals(PostStatus.Drafted, fx.status("stt0003"))
        assertEquals(setOf("staging_20261012"), fx.memory.allAccountMetrics.keys)
    }

    @Test
    fun anAccountWithoutATokenIsNotSnapshotted() = runTest {
        val fx = AgentFixture()
        fx.memory.put(testAccount())

        runner(fx).run()

        assertTrue(fx.threads.calls.isEmpty())
    }

    @Test
    fun aCollectorCrashDoesNotFailTheTickOrTheOtherAccounts() = runTest {
        val fx = AgentFixture()
        fx.memory.put(fx.publishable(testAccount(id = "pl", codePrefix = "pl", language = "pl")))
        fx.memory.put(fx.publishable(testAccount(id = "ua", codePrefix = "uk")))
        fx.put(livePost("plt0001", "pl"))
        fx.put(livePost("ukt0001", "ua"))
        fx.threads.failNext("followersCount", IllegalStateException("boom ?access_token=SECRET123"))

        val summary = runner(fx).run()

        assertTrue(summary.ok)
        assertEquals(0, summary.failedAccounts)
        assertEquals(setOf("ua_20261012"), fx.memory.allAccountMetrics.keys)
        assertEquals(setOf("plt0001_20261012", "ukt0001_20261012"), fx.memory.allMetrics.keys)
        assertFalse("SECRET123" in fx.memory.events.values.joinToString { it.detail.orEmpty() })
        assertFalse("SECRET123" in fx.sentTexts().joinToString())
        assertTrue(fx.memory.events.values.none { it.type == EventType.Pause })
    }

    @Test
    fun aRateLimitedInsightsCallAlertsOncePerDayAndNeverPauses() = runTest {
        val fx = AgentFixture()
        fx.memory.put(fx.publishable(testAccount()))
        fx.put(livePost("stt0001", "staging"))
        fx.threads.insightsFailure = { _, _ -> ThreadsFailure.RateLimited("Threads rate limit reached (HTTP 400, code 4)") }
        val tick = runner(fx)

        tick.run()
        fx.clock.advance(Duration.ofMinutes(60))
        tick.run()
        assertEquals(1, fx.sentTexts().count { "insights for staging failed" in it })

        fx.clock.advance(Duration.ofDays(1))
        tick.run()
        assertEquals(2, fx.sentTexts().count { "insights for staging failed" in it })
        assertFalse(fx.memory.allAccounts.getValue("staging").paused)
        assertTrue(fx.memory.events.values.none { it.type == EventType.Pause })
    }

    @Test
    fun aPermanentInsightsFailureDoesNotStopPublishing() = runTest {
        val fx = AgentFixture()
        fx.memory.put(fx.publishable(testAccount()))
        fx.put(testPost(status = PostStatus.Approved, scheduledAt = AgentFixture.NOW.minusSeconds(60)))
        fx.threads.insightsFailure = { _, _ -> ThreadsFailure.Permanent(10, "Threads rejected the request (HTTP 400, code 10)") }

        val summary = runner(fx).run()

        assertEquals(1, summary.published)
        assertEquals(PostStatus.Published, fx.status("stt0001"))
        assertFalse(fx.memory.allAccounts.getValue("staging").paused)
        assertEquals(1, fx.sentTexts().count { "insights for staging failed" in it })
    }

    @Test
    fun theWholeTickSharesOneTimeLimit() = runTest {
        val fx = AgentFixture(wrapStore = { HangingStore(it) })
        val summary = runner(fx, timeout = 9.minutes).run()
        assertTrue(summary.timedOut)
        assertFalse(summary.ok)
        assertEquals(9.minutes.inWholeMilliseconds, testScheduler.currentTime)
    }
}
