package com.sirelon.sellsnap

import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.http.callbackUpdateJson
import com.sirelon.sellsnap.http.postScheduler
import com.sirelon.sellsnap.http.postTelegram
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.seed.SeedLoader
import com.sirelon.sellsnap.seed.TipsResult
import com.sirelon.sellsnap.telegram.InlineKeyboardMarkup
import io.ktor.http.*
import io.ktor.server.testing.*
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThreadsAgentEndToEndTest {
    private fun buttons(markup: InlineKeyboardMarkup?): List<String> = markup?.inlineKeyboard.orEmpty().flatten().map { it.text }

    @Test
    fun aSeededTipIsQueuedApprovedOnTelegramPublishedAtItsSlotAndItsPreviewGainsDelete() = testApplication {
        val fx = AgentFixture()
        application { module(fx.graph) }
        val client = createClient { followRedirects = false }

        // Seed: accounts, a token for staging, two tips.
        val loader = SeedLoader(fx.store, fx.clock)
        loader.upsertAccounts(File("seed/accounts.json").readText())
        fx.memory.put(fx.publishable(fx.account("staging")))
        val tips = checkNotNull(javaClass.getResource("/seed/tips-sample.json")).readText()
        assertEquals(listOf("stt0001", "stt0002"), (loader.loadTips("staging", "uk", tips) as TipsResult.Loaded).created)

        // Tick at 08:00 Kyiv: both drafts get a slot and a preview with Approve, Edit and Skip.
        assertEquals(HttpStatusCode.OK, client.postScheduler("/tick").status)
        assertEquals(PostStatus.Pending, fx.status("stt0001"))
        assertEquals(Instant.parse("2026-10-12T05:30:00Z"), fx.post("stt0001").scheduledAt)
        assertEquals(Instant.parse("2026-10-12T16:30:00Z"), fx.post("stt0002").scheduledAt)
        val previews = fx.telegram.callsOf<FakeTelegramApi.Call.Sent>()
        assertEquals(2, previews.size)
        assertTrue(previews.all { buttons(it.markup as? InlineKeyboardMarkup) == listOf("Approve", "Edit", "Skip") })
        val messageId = assertNotNull(fx.post("stt0001").telegramMessageId)

        // The owner taps Approve; the webhook handles it.
        val approve = client.postTelegram(callbackUpdateJson(updateId = 100, data = "a:stt0001", messageId = messageId))
        assertEquals(HttpStatusCode.OK, approve.status)
        assertEquals(PostStatus.Approved, fx.status("stt0001"))
        assertEquals(PostStatus.Pending, fx.status("stt0002"))

        // Before the slot a tick publishes nothing.
        client.postScheduler("/tick")
        assertEquals(PostStatus.Approved, fx.status("stt0001"))
        assertTrue(fx.threads.published.isEmpty())

        // After the slot the tick publishes it and refreshes the preview with a Delete button.
        fx.clock.advance(Duration.ofMinutes(31))
        assertEquals(HttpStatusCode.OK, client.postScheduler("/tick").status)

        val published = fx.post("stt0001")
        assertEquals(PostStatus.Published, published.status)
        assertNotNull(published.mediaId)
        assertEquals(1, fx.threads.published.size)
        assertEquals(1, fx.account("staging").publishedCount)
        val refresh = fx.telegram.callsOf<FakeTelegramApi.Call.Edited>().last { it.messageId == messageId }
        assertEquals(listOf("Delete"), buttons(refresh.markup))
        assertEquals(PostStatus.Pending, fx.status("stt0002"))
    }
}
