package com.sirelon.sellsnap.model

import java.time.Instant

/**
 * One weekly drafting batch, `social_batches/{isoWeek}`. A run holds the lease while [runId] is set and
 * [leaseUntil] is in the future; it renews the lease after every slot write.
 */
data class SocialBatch(
    val week: String,
    val runId: String? = null,
    val leaseUntil: Instant? = null,
    val startedAt: Instant? = null,
    /** Set when every account that the batch covers is done. */
    val finishedAt: Instant? = null,
    val accounts: Map<String, BatchAccount> = emptyMap(),
    val lastError: String? = null,
    /**
     * Ids of the accounts whose Sunday digest went out for this batch week. It is a set of its own, not part of
     * [accounts]: a paused account gets the digest but takes no part in drafting, and an entry in [accounts] would count as unfinished drafting.
     */
    val digestSent: Set<String> = emptySet(),
) {
    fun leaseHeld(now: Instant): Boolean = runId != null && leaseUntil?.isAfter(now) == true
}

/**
 * One account's progress in a batch. [failedSlots] holds `<dow>-<slot>` of every slot whose draft failed
 * in the latest run; a rerun retries them.
 */
data class BatchAccount(
    val done: Boolean = false,
    val slotsWritten: Int = 0,
    val failedSlots: List<String> = emptyList(),
)
