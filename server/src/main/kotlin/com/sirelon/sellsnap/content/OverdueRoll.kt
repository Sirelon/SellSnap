package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import java.time.Instant

/** The scheduled instants of every post that occupies a slot or a day (D13). */
fun occupiedSlots(posts: List<SocialPost>): List<Instant> = posts
    .filter { it.status in SlotPlanner.SLOT_HOLDERS }
    .mapNotNull { it.scheduledAt }

/**
 * Gives every overdue `approved` or `pending` post of [accountId] (slot passed, not published) the
 * next free slot (D14). Each move is a conditional transaction. Returns the posts that moved, as
 * stored after the move; the caller refreshes their previews.
 */
suspend fun SocialStore.rollOverdue(accountId: String, now: Instant): List<SocialPost> {
    val account = account(accountId) ?: return emptyList()
    val posts = postsOf(accountId)
    val overdue = posts
        .filter { (it.status == PostStatus.Approved || it.status == PostStatus.Pending) && it.scheduledAt?.isAfter(now) == false }
        .sortedBy { it.scheduledAt }
    if (overdue.isEmpty()) return emptyList()
    val overdueIds = overdue.map { it.id }.toSet()
    val slots = SlotPlanner.roll(
        now = now,
        zone = account.zone,
        publishedCount = account.publishedCount,
        fixed = occupiedSlots(posts.filter { it.id !in overdueIds }),
        toRoll = overdue.map { SlotPlanner.Rollable(it.id, SlotPlanner.requiresSlot2(it.kind, it.pillar)) },
    )
    return overdue.mapNotNull { post ->
        val slot = slots[post.id] ?: return@mapNotNull null
        transaction { tx ->
            val current = tx.post(post.id)
            if (current == null || current.status != post.status) {
                null
            } else {
                current.copy(scheduledAt = slot).also(tx::putPost)
            }
        }
    }
}
