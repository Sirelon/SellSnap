package com.sirelon.sellsnap.insights

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.threads.AlertType
import com.sirelon.sellsnap.threads.ThreadsFailure
import com.sirelon.sellsnap.tick.Alerts
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsCollectorTest {
    private val fx = AgentFixture()
    private val collector = InsightsCollector(fx.store, fx.threads, fx.clock)
    private val day = "20261012"

    private fun account(paused: Boolean = false): SocialAccount = fx.publishable(testAccount()).copy(paused = paused)

    private fun published(
        id: String = "stt0001",
        daysAgo: Long = 2,
        mediaId: String? = "media-$id",
        status: PostStatus = PostStatus.Published,
    ): SocialPost = testPost(id = id, status = status).copy(
        mediaId = mediaId,
        publishedAt = fx.clock.instant().minus(Duration.ofDays(daysAgo)),
    )

    private fun setUp(paused: Boolean = false, vararg posts: SocialPost) {
        fx.memory.put(account(paused))
        posts.forEach(fx::put)
    }

    private fun insightsCalls() = fx.threads.callsTo("mediaInsights")

    @Test
    fun collectsOncePerAccountPerLocalDay() = runTest {
        setUp(posts = arrayOf(published()))

        val first = collector.collect("staging")
        val second = collector.collect("staging")

        assertTrue(first.collected)
        assertEquals(1, first.posts)
        assertTrue(second.alreadyDone)
        // One post: the four counts in one call, `views` in another.
        assertEquals(2, insightsCalls().size)
        assertEquals(1, fx.threads.callsTo("followersCount").size)
        val post = fx.memory.allMetrics.getValue("stt0001_$day")
        assertEquals(listOf(100L, 10L, 2L, 1L, 0L), listOf(post.views, post.likes, post.replies, post.reposts, post.quotes))
        assertEquals("staging", post.accountId)
        assertEquals(fx.clock.instant(), post.at)
        val followers = fx.memory.allAccountMetrics.getValue("staging_$day")
        assertEquals(1_234L, followers.followersCount)

        fx.clock.advance(Duration.ofDays(1))
        assertTrue(collector.collect("staging").collected)
        assertEquals(setOf("stt0001_20261012", "stt0001_20261013"), fx.memory.allMetrics.keys)
    }

    @Test
    fun theDayIsTheAccountsLocalDate() = runTest {
        setUp(posts = arrayOf(published()))
        // 21:30 UTC on 12 October is 00:30 on 13 October in Kyiv.
        fx.clock.now = Instant.parse("2026-10-12T21:30:00Z")

        collector.collect("staging")

        assertEquals(setOf("stt0001_20261013"), fx.memory.allMetrics.keys)
        assertEquals(setOf("staging_20261013"), fx.memory.allAccountMetrics.keys)
    }

    @Test
    fun postsOlderThanFourteenDaysAndPostsWithoutLiveMediaAreSkipped() = runTest {
        setUp(
            posts = arrayOf(
                published(id = "stt0001", daysAgo = 13),
                published(id = "stt0002", daysAgo = 14),
                published(id = "stt0003", daysAgo = 30),
                published(id = "stt0004", mediaId = null),
                published(id = "stt0005", status = PostStatus.Deleted),
                published(id = "stt0006", status = PostStatus.Approved),
            ),
        )

        val outcome = collector.collect("staging")

        assertEquals(1, outcome.posts)
        assertEquals(setOf("stt0001_$day"), fx.memory.allMetrics.keys)
    }

    @Test
    fun aViewsFailureKeepsTheOtherMetrics() = runTest {
        setUp(posts = arrayOf(published()))
        fx.threads.insightsFailure = { _, metrics ->
            if ("views" in metrics) ThreadsFailure.Permanent(100, "Threads rejected the request (HTTP 400, code 100)") else null
        }

        val outcome = collector.collect("staging")

        val post = fx.memory.allMetrics.getValue("stt0001_$day")
        assertNull(post.views)
        assertEquals(10L, post.likes)
        assertEquals(2L, post.replies)
        assertTrue(outcome.alerts.isEmpty())
        assertTrue(outcome.collected)
    }

    @Test
    fun aTransientViewsFailureIsSilentAndDoesNotHoldTheDayOpen() = runTest {
        setUp(posts = arrayOf(published()))
        fx.threads.insightsFailure = { _, metrics ->
            if ("views" in metrics) ThreadsFailure.Transient("Threads server error (HTTP 503)") else null
        }

        val first = collector.collect("staging")

        assertTrue(first.alerts.isEmpty())
        assertTrue(first.collected)
        assertNull(fx.memory.allMetrics.getValue("stt0001_$day").views)
        assertEquals(10L, fx.memory.allMetrics.getValue("stt0001_$day").likes)
    }

    @Test
    fun aPermanentFailureAlertsOncePerDayAndNeverPausesTheAccount() = runTest {
        setUp(posts = arrayOf(published()))
        fx.threads.insightsFailure = { _, _ -> ThreadsFailure.Permanent(100, "Threads rejected the request (HTTP 400, code 100)") }
        val alerts = Alerts(fx.store, fx.telegram, fx.clock, AgentFixture.OWNER)

        suspend fun tick() = collector.collect("staging").alerts.forEach { alerts.sendOnce(it) }

        tick()
        tick()
        assertEquals(1, fx.sentTexts().count { "insights for staging failed" in it })
        assertFalse(fx.memory.allAccounts.getValue("staging").paused)
        assertTrue(fx.memory.events.values.none { it.type == EventType.Pause })
        // The permanent failure still marks the day, so the second tick called nothing.
        assertEquals(1, insightsCalls().size)
        assertEquals(1, fx.threads.callsTo("followersCount").size)

        fx.clock.advance(Duration.ofDays(1))
        tick()
        assertEquals(2, fx.sentTexts().count { "insights for staging failed" in it })
        assertFalse(fx.memory.allAccounts.getValue("staging").paused)
    }

    @Test
    fun aPermanentFollowersFailureStoresANullCountAndMarksTheDay() = runTest {
        setUp(posts = arrayOf(published()))
        fx.threads.failNext("followersCount", ThreadsFailure.Permanent(10, "Threads rejected the request (HTTP 400, code 10)"))

        val outcome = collector.collect("staging")

        assertEquals(1, outcome.alerts.size)
        assertEquals(1, outcome.posts)
        assertNull(fx.memory.allAccountMetrics.getValue("staging_$day").followersCount)
        assertTrue(collector.collect("staging").alreadyDone)
    }

    @Test
    fun aRateLimitAlertsStopsTheRunAndIsRetriedOnTheNextTick() = runTest {
        setUp(posts = arrayOf(published(id = "stt0001"), published(id = "stt0002", daysAgo = 1)))
        fx.threads.failNext("mediaInsights", ThreadsFailure.RateLimited("Threads rate limit reached (HTTP 400, code 4)"))

        val outcome = collector.collect("staging")

        assertEquals(AlertType.InsightsFailed, outcome.alerts.single().type)
        assertFalse(outcome.collected)
        assertEquals(1, insightsCalls().size)
        assertTrue(fx.threads.callsTo("followersCount").isEmpty())
        assertTrue(fx.memory.allAccountMetrics.isEmpty())
        assertFalse(fx.memory.allAccounts.getValue("staging").paused)

        assertTrue(collector.collect("staging").collected)
        assertEquals(2, fx.memory.allMetrics.size)
    }

    @Test
    fun aTransientFollowersFailureIsSilentAndLeavesTheDayOpen() = runTest {
        setUp(posts = arrayOf(published()))
        fx.threads.failNext("followersCount", ThreadsFailure.Transient("Threads server error (HTTP 500)"))

        val outcome = collector.collect("staging")

        assertTrue(outcome.alerts.isEmpty())
        assertFalse(outcome.collected)
        assertTrue(fx.memory.allAccountMetrics.isEmpty())
        assertTrue(collector.collect("staging").collected)
    }

    @Test
    fun aPausedAccountIsStillSnapshotted() = runTest {
        setUp(paused = true, posts = arrayOf(published()))

        val outcome = collector.collect("staging")

        assertTrue(outcome.collected)
        assertEquals(setOf("stt0001_$day"), fx.memory.allMetrics.keys)
        assertTrue(fx.memory.allAccounts.getValue("staging").paused)
    }

    @Test
    fun aCrashBeforeTheFollowerCountLandsIsRetriedOnTheNextTick() = runTest {
        setUp(posts = arrayOf(published()))
        fx.threads.failNext("followersCount", IllegalStateException("boom"))

        assertFailsWith<IllegalStateException> { collector.collect("staging") }
        assertTrue(fx.memory.allAccountMetrics.isEmpty())
        assertEquals(1, fx.memory.allMetrics.size)

        val retry = collector.collect("staging")

        assertTrue(retry.collected)
        assertEquals(setOf("staging_$day"), fx.memory.allAccountMetrics.keys)
        assertEquals(1, fx.memory.allMetrics.size)
    }

    @Test
    fun forceCollectsAgainAndOverwritesTheDay() = runTest {
        setUp(posts = arrayOf(published()))
        collector.collect("staging")
        fx.threads.followers = 2_000
        fx.threads.defaultInsights = fx.threads.defaultInsights + ("likes" to 11L)

        assertTrue(collector.collect("staging").alreadyDone)
        val forced = collector.collect("staging", force = true)

        assertTrue(forced.collected)
        assertEquals(2_000L, fx.memory.allAccountMetrics.getValue("staging_$day").followersCount)
        assertEquals(11L, fx.memory.allMetrics.getValue("stt0001_$day").likes)
        assertEquals(1, fx.memory.allMetrics.size)
    }

    @Test
    fun anAccountWithoutATokenIsLeftAlone() = runTest {
        fx.memory.put(testAccount())

        val outcome = collector.collect("staging")

        assertEquals(0, outcome.posts)
        assertTrue(fx.threads.calls.isEmpty())
        assertTrue(fx.memory.allAccountMetrics.isEmpty())
    }

    @Test
    fun theAlertDetailNeverCarriesATokenOrAUrl() = runTest {
        setUp(posts = arrayOf(published()))
        fx.threads.insightsFailure = { _, _ -> ThreadsFailure.Permanent(190, "Threads rejected the request ?access_token=SECRET123") }

        val detail = collector.collect("staging").alerts.single().detail.orEmpty()

        assertFalse("SECRET123" in detail)
    }
}
