package com.sirelon.sellsnap.store

import com.sirelon.sellsnap.model.AccountMetrics
import com.sirelon.sellsnap.model.PostMetrics
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialClick
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.model.UsageCounters
import java.time.Instant

/** Persistence port. Queries use single-field equality; callers filter further in memory. */
interface SocialStore {
    suspend fun account(id: String): SocialAccount?

    suspend fun accounts(): List<SocialAccount>

    suspend fun post(id: String): SocialPost?

    suspend fun postsOf(accountId: String): List<SocialPost>

    suspend fun postsWithStatus(status: PostStatus): List<SocialPost>

    /**
     * Runs [block] atomically. The block may run more than once, so it must have no side effects
     * beyond [StoreTx] writes: no Telegram or Threads calls. Make those after the transaction returns.
     */
    suspend fun <T> transaction(block: (StoreTx) -> T): T

    /** True when [updateId] was not seen before; the record expires at [expireAt]. */
    suspend fun claimUpdate(updateId: Long, expireAt: Instant): Boolean

    /** Writes [event] under its id unless that id exists; true when written. Used for daily alert dedup. */
    suspend fun recordEvent(event: SocialEvent): Boolean

    suspend fun logClick(click: SocialClick)

    /** Writes the post's snapshot for its day, replacing an earlier one of the same day. */
    suspend fun saveMetrics(metrics: PostMetrics)

    /** The post snapshots of [accountId] taken on [day] (`yyyyMMdd`). */
    suspend fun metricsOf(accountId: String, day: String): List<PostMetrics>

    suspend fun accountMetrics(accountId: String, day: String): AccountMetrics?

    /** Writes the account-day document unless it exists; true when written. This marks the day as collected. */
    suspend fun claimAccountDay(metrics: AccountMetrics): Boolean

    /** Writes the account-day document, replacing an existing one; used by a forced collection. */
    suspend fun saveAccountMetrics(metrics: AccountMetrics)

    /** Adds [delta] to [model]'s counters in `social_spend/{day}` (`yyyyMMdd`, UTC) atomically; concurrent calls never lose an increment. */
    suspend fun addUsage(day: String, model: String, delta: UsageCounters)

    /** The day's counters per model; empty when nothing was recorded. */
    suspend fun usageOf(day: String): Map<String, UsageCounters>
}

/**
 * Transaction view. Reads must precede writes (a Firestore rule); reads never see the
 * transaction's own writes.
 */
interface StoreTx {
    fun account(id: String): SocialAccount?

    fun post(id: String): SocialPost?

    fun putAccount(account: SocialAccount)

    fun putPost(post: SocialPost)

    fun putEvent(event: SocialEvent)
}

/** Moves a post out of [from] with [change]; null when the post is missing or no longer in [from]. */
suspend fun SocialStore.transitionPost(
    id: String,
    from: PostStatus,
    change: (SocialPost) -> SocialPost,
): SocialPost? = transaction { tx ->
    val current = tx.post(id)
    if (current == null || current.status != from) {
        null
    } else {
        change(current).also(tx::putPost)
    }
}
