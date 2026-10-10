package com.sirelon.sellsnap

import com.sirelon.sellsnap.fakes.DraftingLlm
import com.sirelon.sellsnap.http.callbackUpdateJson
import com.sirelon.sellsnap.http.postScheduler
import com.sirelon.sellsnap.http.postTelegram
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.seed.SeedLoader
import io.ktor.http.*
import io.ktor.server.testing.*
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A whole week on fakes: the Sunday batch drafts the Ukrainian and the Polish account, the owner taps Approve on
 * every preview, and hourly ticks, at minute 30 like the real scheduler job, run until the week has ended. The week
 * crosses the 25 October 2026 change from summer to winter time in both zones.
 */
class ThreadsAgentWeekTest {
    @Test
    fun aWeekOfDraftsApprovedOnTelegramPublishesEveryPostAtItsLocalSlotAcrossTheDstChange() = testApplication {
        val llm = DraftingLlm()
        val fx = AgentFixture(now = SUNDAY_09_KYIV, llm = llm.client)
        application { module(fx.graph) }
        val client = createClient { followRedirects = false }

        SeedLoader(fx.store, fx.clock).upsertAccounts(File("seed/accounts.json").readText())
        for (id in listOf("ua", "pl")) fx.memory.put(fx.publishable(fx.account(id)))

        // Sunday 09:00 Kyiv: the batch drafts both accounts and sends every preview.
        val batch = client.postScheduler("/batch")
        assertEquals(HttpStatusCode.OK, batch.status)
        val drafts = fx.memory.allPosts.values.filter { it.source == PostSource.Drafter }
        assertEquals(24, drafts.size)
        assertEquals(setOf("2026-W43"), drafts.map { it.batchWeek }.toSet())
        assertTrue(drafts.all { it.status == PostStatus.Pending })

        // The owner taps Approve on every preview.
        var updateId = 1L
        for (post in drafts.sortedWith(compareBy({ it.accountId }, { it.scheduledAt }))) {
            val messageId = assertNotNull(post.telegramMessageId, "${post.code} has a preview")
            val tap = client.postTelegram(callbackUpdateJson(updateId = updateId++, data = "a:${post.id}", messageId = messageId))
            assertEquals(HttpStatusCode.OK, tap.status)
        }
        assertTrue(fx.memory.allPosts.values.all { it.status == PostStatus.Approved })

        // Hourly ticks at minute 30, until Sunday 25 October has ended in both zones.
        fx.clock.now = FIRST_TICK
        while (fx.clock.instant() <= LAST_TICK) {
            assertEquals(HttpStatusCode.OK, client.postScheduler("/tick").status)
            fx.clock.advance(Duration.ofHours(1))
        }

        val posts = fx.memory.allPosts.values.sortedWith(compareBy({ it.accountId }, { it.scheduledAt }))
        assertEquals(24, posts.size)
        for (post in posts) {
            val zone = fx.account(post.accountId).zone
            val slot = assertNotNull(post.scheduledAt).atZone(zone).toLocalTime()
            assertTrue(slot == LocalTime.of(8, 30) || slot == LocalTime.of(19, 30), "${post.code} sits at $slot local time")
            assertEquals(PostStatus.Published, post.status, post.code)
            assertEquals(post.scheduledAt, post.publishedAt, "${post.code} went out at its slot")
        }
        assertSundayAfterTheDstChange(posts, "ua", Instant.parse("2026-10-25T06:30:00Z"))
        assertSundayAfterTheDstChange(posts, "pl", Instant.parse("2026-10-25T07:30:00Z"))
        assertEquals(24, fx.threads.published.size)
        for (id in listOf("ua", "pl")) {
            val account = fx.account(id)
            assertEquals(12, account.publishedCount, id)
            assertEquals(AccountMode.Approval, account.mode, id)
            assertEquals(12, account.approvalStreak, id)
        }

        val events = fx.memory.events.values
        assertTrue(events.none { it.type in setOf(EventType.Pause, EventType.Resume, EventType.ModeChange, EventType.Delete, EventType.Error) }, events.toString())
        assertTrue(fx.sentTexts().none { "did not finish" in it }, "the batch finished, so the tick raised no alert")
    }

    private fun assertSundayAfterTheDstChange(posts: List<SocialPost>, accountId: String, expected: Instant) {
        val last = posts.filter { it.accountId == accountId }.last()
        assertEquals(expected, last.scheduledAt, "$accountId Sunday 08:30 local time after the change")
        assertEquals(expected, last.publishedAt)
    }

    private companion object {
        /** Sunday 18 October 2026, 09:00 in Kyiv (UTC+3). */
        val SUNDAY_09_KYIV: Instant = Instant.parse("2026-10-18T06:00:00Z")

        /** The first scheduler tick after the batch, at minute 30. */
        val FIRST_TICK: Instant = Instant.parse("2026-10-18T06:30:00Z")

        /** Sunday 25 October 23:30 in Kyiv (UTC+2 after the change). */
        val LAST_TICK: Instant = Instant.parse("2026-10-25T21:30:00Z")
    }
}
