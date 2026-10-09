package com.sirelon.sellsnap.tick

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.telegram.InlineKeyboardMarkup
import com.sirelon.sellsnap.telegram.PreviewSender
import com.sirelon.sellsnap.telegram.Previews
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueFillerTest {
    private val slot1 = Instant.parse("2026-10-12T05:30:00Z")
    private val slot2 = Instant.parse("2026-10-12T16:30:00Z")

    private fun filler(fx: AgentFixture): QueueFiller {
        val previews = Previews(fx.images, fx.config.publicBaseUrl)
        return QueueFiller(fx.store, PreviewSender(fx.telegram, previews, fx.store, AgentFixture.OWNER), previews, fx.clock)
    }

    private fun AgentFixture.draft(id: String, createdAt: String = "2026-10-01T00:00:00Z") =
        put(testPost(id = id, status = PostStatus.Drafted).copy(createdAt = Instant.parse(createdAt)))

    private fun buttons(call: FakeTelegramApi.Call.Sent): List<String> =
        (call.markup as? InlineKeyboardMarkup)?.inlineKeyboard.orEmpty().flatten().map { it.text }

    @Test
    fun approvalModeQueuesTheDraftAsPendingAndSendsItsPreview() = runTest {
        val fx = AgentFixture().also { it.memory.put(testAccount()); it.draft("stt0001") }
        val result = filler(fx).fill("staging")

        assertEquals(1, result.queued)
        val post = fx.post("stt0001")
        assertEquals(PostStatus.Pending, post.status)
        assertEquals(slot1, post.scheduledAt)
        assertEquals(false, post.autoApproved)
        val sent = fx.telegram.callsOf<FakeTelegramApi.Call.Sent>().single()
        assertEquals(listOf("Approve", "Edit", "Skip"), buttons(sent))
        assertEquals(sent.messageId, post.telegramMessageId)
    }

    @Test
    fun autoModeApprovesWithADeleteBeforeSlotPreview() = runTest {
        val fx = AgentFixture().also { it.memory.put(testAccount().copy(mode = AccountMode.Auto)); it.draft("stt0001") }
        filler(fx).fill("staging")

        val post = fx.post("stt0001")
        assertEquals(PostStatus.Approved, post.status)
        assertTrue(post.autoApproved)
        assertEquals(listOf("Delete before slot"), buttons(fx.telegram.callsOf<FakeTelegramApi.Call.Sent>().single()))
    }

    @Test
    fun aPostThatNeedsAttentionIsNeverAutoApproved() = runTest {
        val fx = AgentFixture().also {
            it.memory.put(testAccount().copy(mode = AccountMode.Auto))
            it.put(testPost(id = "stt0001", status = PostStatus.Drafted).copy(needsAttention = true))
        }
        filler(fx).fill("staging")

        val post = fx.post("stt0001")
        assertEquals(PostStatus.Pending, post.status)
        assertEquals(false, post.autoApproved)
        assertEquals(listOf("Approve", "Edit", "Skip"), buttons(fx.telegram.callsOf<FakeTelegramApi.Call.Sent>().single()))
    }

    @Test
    fun theOldestDraftsTakeTheNextSlotsAndNothingBeyond24HoursIsQueued() = runTest {
        val fx = AgentFixture().also {
            it.memory.put(testAccount())
            it.draft("stt0003", createdAt = "2026-10-03T00:00:00Z")
            it.draft("stt0001", createdAt = "2026-10-01T00:00:00Z")
            it.draft("stt0002", createdAt = "2026-10-02T00:00:00Z")
        }
        val result = filler(fx).fill("staging")

        assertEquals(2, result.queued)
        assertEquals(slot1, fx.post("stt0001").scheduledAt)
        assertEquals(slot2, fx.post("stt0002").scheduledAt)
        assertEquals(PostStatus.Drafted, fx.post("stt0003").status)
        assertNull(fx.post("stt0003").scheduledAt)
    }

    @Test
    fun aSlotJustInsideTheHorizonIsUsedAndOneJustOutsideIsNot() = runTest {
        // Past the launch period only slot 1 exists. At Monday 20:00 Kyiv, Tuesday 08:30 is 12.5 hours away.
        val inside = AgentFixture(now = Instant.parse("2026-10-12T17:00:00Z")).also {
            it.memory.put(testAccount(publishedCount = 10)); it.draft("stt0001"); it.draft("stt0002")
        }
        filler(inside).fill("staging")
        assertEquals(Instant.parse("2026-10-13T05:30:00Z"), inside.post("stt0001").scheduledAt)
        assertEquals(PostStatus.Drafted, inside.status("stt0002"))

        // At Tuesday 08:31 Kyiv the next slot is Wednesday 08:30, 23h59m away: inside.
        // A second draft would need Thursday, which is outside.
        val edge = AgentFixture(now = Instant.parse("2026-10-13T05:31:00Z")).also {
            it.memory.put(testAccount(publishedCount = 10)); it.draft("stt0001"); it.draft("stt0002")
        }
        filler(edge).fill("staging")
        assertEquals(Instant.parse("2026-10-14T05:30:00Z"), edge.post("stt0001").scheduledAt)
        assertEquals(PostStatus.Drafted, edge.status("stt0002"))
    }

    @Test
    fun aMissingPreviewIsSentAgainButAnApprovedPostWithoutButtonsIsLeftAlone() = runTest {
        val fx = AgentFixture().also {
            it.memory.put(testAccount())
            it.put(testPost(id = "stt0001", status = PostStatus.Pending, scheduledAt = slot1))
            // An approval-mode approved post has no buttons: a `/post now` post, reported inline.
            it.put(testPost(id = "stt0002", status = PostStatus.Approved, scheduledAt = slot2))
        }
        val result = filler(fx).fill("staging")

        assertEquals(1, result.resent)
        assertNotNull(fx.post("stt0001").telegramMessageId)
        assertNull(fx.post("stt0002").telegramMessageId)
        assertEquals(1, fx.telegram.callsOf<FakeTelegramApi.Call.Sent>().size)

        // A second fill sends nothing more.
        assertEquals(0, filler(fx).fill("staging").resent)
    }

    @Test
    fun aFailedPreviewIsRetriedOnTheNextFill() = runTest {
        val fx = AgentFixture().also { it.memory.put(testAccount()); it.draft("stt0001") }
        fx.telegram.failNext("sendMessage", com.sirelon.sellsnap.telegram.TelegramFailure("down"))
        runCatching { filler(fx).fill("staging") }

        assertEquals(PostStatus.Pending, fx.status("stt0001"))
        assertNull(fx.post("stt0001").telegramMessageId)
        filler(fx).fill("staging")
        assertNotNull(fx.post("stt0001").telegramMessageId)
    }

    @Test
    fun overduePendingAndApprovedPostsRollToTheNextFreeSlot() = runTest {
        val fx = AgentFixture().also {
            it.memory.put(testAccount().copy(mode = AccountMode.Auto))
            it.put(
                testPost(id = "stt0001", status = PostStatus.Pending, scheduledAt = AgentFixture.NOW.minusSeconds(3600))
                    .copy(telegramMessageId = 900),
            )
            it.put(
                testPost(id = "stt0002", status = PostStatus.Approved, scheduledAt = AgentFixture.NOW.minusSeconds(7200))
                    .copy(telegramMessageId = 901, autoApproved = true),
            )
            it.put(testPost(id = "stt0003", status = PostStatus.Published, scheduledAt = AgentFixture.NOW.minusSeconds(86_400)))
        }
        val result = filler(fx).fill("staging")

        assertEquals(2, result.rolled)
        // The most overdue post goes first.
        assertEquals(slot1, fx.post("stt0002").scheduledAt)
        assertEquals(slot2, fx.post("stt0001").scheduledAt)
        assertEquals(PostStatus.Pending, fx.status("stt0001"))
        assertEquals(PostStatus.Approved, fx.status("stt0002"))
        assertEquals(setOf(900L, 901L), fx.telegram.callsOf<FakeTelegramApi.Call.Edited>().map { it.messageId }.toSet())
    }
}
