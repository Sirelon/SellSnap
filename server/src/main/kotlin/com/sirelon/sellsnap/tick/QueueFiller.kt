package com.sirelon.sellsnap.tick

import com.sirelon.sellsnap.content.SlotPlanner
import com.sirelon.sellsnap.content.occupiedSlots
import com.sirelon.sellsnap.content.rollOverdue
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.telegram.PreviewSender
import com.sirelon.sellsnap.telegram.Previews
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** What one fill did for one account. */
data class FillResult(val rolled: Int = 0, val queued: Int = 0, val resent: Int = 0)

/**
 * Keeps an account's near future supplied with posts: rolls overdue ones to new slots (D14), gives
 * the oldest `drafted` posts a free slot within [horizon], and sends their previews. In approval
 * mode a queued post is `pending`; in auto mode it is `approved` with a Delete-before-slot preview,
 * except a post that needs attention, which always waits for the owner.
 */
class QueueFiller(
    private val store: SocialStore,
    private val sender: PreviewSender,
    private val previews: Previews,
    private val clock: Clock,
    private val horizon: Duration = Duration.ofHours(24),
) {
    suspend fun fill(accountId: String): FillResult {
        val now = clock.instant()
        val rolled = rollOverdue(accountId, now)
        val queued = queueDrafts(accountId, now)
        val resent = resendMissingPreviews(accountId)
        return FillResult(rolled = rolled, queued = queued, resent = resent)
    }

    private suspend fun rollOverdue(accountId: String, now: Instant): Int {
        val moved = store.rollOverdue(accountId, now)
        moved.forEach { sender.refresh(it) }
        return moved.size
    }

    private suspend fun queueDrafts(accountId: String, now: Instant): Int {
        val account = store.account(accountId) ?: return 0
        val posts = store.postsOf(accountId)
        val drafts = posts.filter { it.status == PostStatus.Drafted }.sortedWith(compareBy({ it.createdAt }, { it.code }))
        if (drafts.isEmpty()) return 0

        val taken = occupiedSlots(posts).toMutableList()
        var queued = 0
        for (draft in drafts) {
            val slot = SlotPlanner.nextFreeSlot(
                now = now,
                zone = account.zone,
                publishedCount = account.publishedCount,
                slot2Only = SlotPlanner.requiresSlot2(draft.kind, draft.pillar),
                taken = taken,
                notAfter = now.plus(horizon),
            ) ?: continue
            val queuedPost = assign(draft.id, slot) ?: continue
            taken += slot
            queued++
            sender.send(queuedPost)
        }
        return queued
    }

    /** `drafted -> pending` (or `approved` in auto mode) with [slot]; null when the post changed meanwhile. */
    private suspend fun assign(postId: String, slot: Instant): SocialPost? = store.transaction { tx ->
        val current = tx.post(postId)
        val account = current?.let { tx.account(it.accountId) }
        if (current == null || account == null || current.status != PostStatus.Drafted) {
            null
        } else {
            val auto = account.mode == AccountMode.Auto && !current.needsAttention
            current.copy(
                scheduledAt = slot,
                status = if (auto) PostStatus.Approved else PostStatus.Pending,
                autoApproved = auto,
            ).also(tx::putPost)
        }
    }

    /**
     * Sends the preview again for a post that should carry buttons but has no Telegram message,
     * for example after a Telegram outage. Approved posts without buttons, such as `/post now`
     * posts, are left alone.
     */
    private suspend fun resendMissingPreviews(accountId: String): Int {
        val account = store.account(accountId) ?: return 0
        val missing = store.postsOf(accountId)
            .filter { it.status in PREVIEWED && it.telegramMessageId == null }
            .filter { previews.keyboard(it, account) != null }
            .sortedBy { it.scheduledAt }
        missing.forEach { sender.send(it) }
        return missing.size
    }

    private companion object {
        val PREVIEWED = setOf(PostStatus.Pending, PostStatus.Approved)
    }
}
