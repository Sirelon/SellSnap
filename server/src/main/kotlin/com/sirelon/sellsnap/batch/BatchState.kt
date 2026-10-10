package com.sirelon.sellsnap.batch

import com.sirelon.sellsnap.store.SocialStore
import java.time.Instant

/** The batch weeks that can hold an account: the ISO week of today and the week a run now drafts for. */
private fun relevantWeeks(now: Instant): List<String> =
    listOf(IsoWeek.key(now), IsoWeek.key(IsoWeek.targetMonday(now))).distinct()

/**
 * True while a batch run holds its lease and has not finished drafting [accountId]. The tick then leaves the
 * account's queue alone and `/post` without `now` asks the owner to wait.
 */
suspend fun SocialStore.batchDrafting(accountId: String, now: Instant): Boolean =
    relevantWeeks(now).any { week ->
        val batch = batch(week) ?: return@any false
        batch.leaseHeld(now) && batch.accounts[accountId]?.done == false
    }

/** True when the batch for the week that a run now drafts for has finished [accountId]. */
suspend fun SocialStore.batchCovered(accountId: String, now: Instant): Boolean =
    batch(IsoWeek.key(IsoWeek.targetMonday(now)))?.accounts?.get(accountId)?.done == true
