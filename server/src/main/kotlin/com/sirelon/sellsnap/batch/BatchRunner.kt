package com.sirelon.sellsnap.batch

import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.brand.ReleaseNotesSource
import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.content.PostCode
import com.sirelon.sellsnap.content.WeekPlanner
import com.sirelon.sellsnap.content.occupiedSlots
import com.sirelon.sellsnap.draft.DraftOutcome
import com.sirelon.sellsnap.draft.DraftPipeline
import com.sirelon.sellsnap.draft.Prompts
import com.sirelon.sellsnap.draft.SlotBrief
import com.sirelon.sellsnap.model.BatchAccount
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SKIP_REASON_EXPIRED
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialBatch
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.spend.UsageTracker
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.store.transitionPost
import com.sirelon.sellsnap.telegram.BotText
import com.sirelon.sellsnap.telegram.PreviewSender
import com.sirelon.sellsnap.telegram.Previews
import com.sirelon.sellsnap.telegram.TelegramApi
import com.sirelon.sellsnap.telegram.TelegramFailure
import com.sirelon.sellsnap.threads.AlertRequest
import com.sirelon.sellsnap.threads.AlertType
import com.sirelon.sellsnap.threads.redact
import com.sirelon.sellsnap.tick.Alerts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/** How one account came out of a run. [slotsWritten] and [failedSlots] describe this run only. */
data class AccountReport(
    val accountId: String,
    val status: AccountStatus,
    val slotsWritten: Int = 0,
    val failedSlots: List<String> = emptyList(),
    /** Why the account stopped, in words without secrets; null for a finished account. */
    val reason: String? = null,
)

enum class AccountStatus {
    /** Every slot is drafted or recorded as failed, the previews are sent and the account is marked done. */
    Done,

    /** The draft model was busy or at its limit; the account stays not done and the next run continues it. */
    Deferred,

    /** Something unexpected threw; the account stays not done. */
    Failed,

    /** Another run took over the lease; this run stopped writing. */
    LeaseLost,
}

/** What one `/batch` call came to. */
sealed interface BatchResult {
    val week: String

    /** Every account that the run took is done. */
    data class Done(override val week: String, val accounts: List<AccountReport>) : BatchResult

    /** Every account that the call covers was done before it. Nothing ran. */
    data class AlreadyDone(override val week: String) : BatchResult

    /** Another run holds the lease. Nothing ran. */
    data class Running(override val week: String) : BatchResult

    /** At least one account stopped because the draft model was busy or at its limit. */
    data class Deferred(override val week: String, val accounts: List<AccountReport>) : BatchResult

    /** The run hit its time limit; what it wrote stays and the next run continues. */
    data class TimedOut(override val week: String) : BatchResult

    /** At least one account threw. */
    data class Failed(override val week: String, val accounts: List<AccountReport>) : BatchResult

    /** One line for the response body and the logs. */
    fun line(): String = text().trimEnd()

    private fun text(): String = when (this) {
        is Done -> "batch $week done ${accounts.describe()}"
        is AlreadyDone -> "batch $week already done"
        is Running -> "batch $week running"
        is Deferred -> "batch $week deferred ${accounts.describe()}"
        is TimedOut -> "batch $week timed out"
        is Failed -> "batch $week failed ${accounts.describe()}"
    }
}

private fun List<AccountReport>.describe(): String =
    joinToString(" ") { "${it.accountId}=${it.status.name.lowercase()}(written=${it.slotsWritten},failed=${it.failedSlots.size})" }

/**
 * The weekly drafting batch (design §3.3). One run drafts the week of [IsoWeek.targetMonday] for every account
 * that has a token, is not paused and has `batchEnabled`, or for the accounts it is given. The run holds a
 * lease in `social_batches/{isoWeek}` that it renews after every write; a second run answers `Running`.
 *
 * Per account, up to [MAX_PARALLEL] at a time: expire the old pending posts, plan the week, draft every free
 * slot in time order, then send the previews and mark the account done. Each slot is written in one transaction
 * that also creates its marker, so a rerun never drafts the same slot twice. A model that is busy or at its limit
 * stops that account without marking it done; the next run continues it.
 */
class BatchRunner(
    private val store: SocialStore,
    private val pipeline: DraftPipeline,
    private val releases: ReleaseNotesSource,
    private val previews: Previews,
    private val sender: PreviewSender,
    private val telegram: TelegramApi,
    private val alerts: Alerts,
    private val usage: UsageTracker,
    private val clock: Clock,
    private val ownerChatId: Long,
    private val timeout: kotlin.time.Duration = 28.minutes,
    private val newRunId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(BatchRunner::class.java)

    private class LeaseLost : Exception("Another batch run holds the lease")

    private sealed interface Claim {
        data object AlreadyDone : Claim

        data object Held : Claim

        class Taken(val accountIds: Set<String>) : Claim
    }

    /** [accountIds] null means every account with a token that is not paused and has `batchEnabled`. */
    suspend fun run(accountIds: List<String>? = null): BatchResult {
        val start = clock.instant()
        val monday = IsoWeek.targetMonday(start)
        val week = IsoWeek.key(monday)
        val targets = targets(accountIds)
        if (targets.isEmpty()) return BatchResult.Done(week, emptyList())
        val runId = newRunId()
        return when (val claimed = claim(week, runId, targets.map { it.id })) {
            Claim.AlreadyDone -> BatchResult.AlreadyDone(week)
            Claim.Held -> BatchResult.Running(week)
            is Claim.Taken -> runClaimed(week, monday, runId, targets.filter { it.id in claimed.accountIds })
        }
    }

    private suspend fun targets(accountIds: List<String>?): List<SocialAccount> {
        val all = store.accounts().sortedBy { it.id }
        return if (accountIds == null) {
            all.filter { it.token != null && !it.paused && it.batchEnabled }
        } else {
            all.filter { it.id in accountIds && it.token != null && !it.paused }
        }
    }

    /**
     * Takes the lease when it is free or expired and lists the accounts to run in the batch document. An account is
     * left out when it is done without failed slots; an account done with failed slots runs again, for those slots only.
     */
    private suspend fun claim(week: String, runId: String, accountIds: List<String>): Claim = store.transaction { tx ->
        val now = clock.instant()
        val current = tx.batch(week) ?: SocialBatch(week)
        val pending = accountIds.filterNot { id -> current.accounts[id]?.let { it.done && it.failedSlots.isEmpty() } == true }
        when {
            pending.isEmpty() -> Claim.AlreadyDone
            current.leaseHeld(now) -> Claim.Held
            else -> {
                val entries = pending.associateWith { (current.accounts[it] ?: BatchAccount()).copy(done = false) }
                tx.putBatch(
                    current.copy(
                        runId = runId,
                        leaseUntil = now.plus(LEASE),
                        startedAt = current.startedAt ?: now,
                        finishedAt = null,
                        accounts = current.accounts + entries,
                    ),
                )
                Claim.Taken(pending.toSet())
            }
        }
    }

    private suspend fun runClaimed(week: String, monday: LocalDate, runId: String, targets: List<SocialAccount>): BatchResult =
        try {
            val reports = withTimeoutReports(week, monday, runId, targets)
            classify(week, reports)
        } catch (e: TimeoutCancellationException) {
            log.error("Batch {} stopped at the {} time limit", week, timeout)
            BatchResult.TimedOut(week)
        } finally {
            // Also on a timeout or a cancellation, so the next run need not wait for the lease to lapse.
            withContext(NonCancellable) { release(week, runId) }
        }

    private suspend fun withTimeoutReports(week: String, monday: LocalDate, runId: String, targets: List<SocialAccount>): List<AccountReport> =
        withTimeout(timeout) {
            val limit = Semaphore(MAX_PARALLEL)
            coroutineScope {
                targets.map { account ->
                    async { limit.withPermit { draftAccount(week, monday, runId, account.id) } }
                }.awaitAll()
            }
        }

    private fun classify(week: String, reports: List<AccountReport>): BatchResult = when {
        reports.any { it.status == AccountStatus.LeaseLost } -> BatchResult.Running(week)
        reports.any { it.status == AccountStatus.Deferred } -> BatchResult.Deferred(week, reports)
        reports.any { it.status == AccountStatus.Failed } -> BatchResult.Failed(week, reports)
        else -> BatchResult.Done(week, reports)
    }

    /** Clears the lease if this run still owns it, and stamps `finishedAt` when every account in the batch is done. */
    private suspend fun release(week: String, runId: String) {
        try {
            store.transaction { tx ->
                val current = tx.batch(week)
                if (current != null && current.runId == runId) {
                    val finished = current.accounts.isNotEmpty() && current.accounts.values.all { it.done }
                    tx.putBatch(
                        current.copy(
                            runId = null,
                            leaseUntil = null,
                            finishedAt = if (finished) clock.instant() else null,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            // The lease lapses on its own after a few minutes.
            log.error("Batch {} could not release its lease: {}", week, e::class.simpleName)
        }
    }

    private suspend fun draftAccount(week: String, monday: LocalDate, runId: String, accountId: String): AccountReport =
        try {
            AccountRun(week, monday, runId, accountId).run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LeaseLost) {
            AccountReport(accountId, AccountStatus.LeaseLost, reason = e.message)
        } catch (e: Exception) {
            val detail = redact(e.message.orEmpty()).replace(Regex("\\s+"), " ").take(MAX_DETAIL)
            log.error("Batch {} failed for {}: {}: {}", week, accountId, e::class.simpleName, detail)
            AccountReport(accountId, AccountStatus.Failed, reason = e::class.simpleName)
        }

    /** One account's pass through the batch. */
    private inner class AccountRun(
        private val week: String,
        private val monday: LocalDate,
        private val runId: String,
        private val accountId: String,
    ) {
        suspend fun run(): AccountReport {
            val account = checkNotNull(store.account(accountId)) { "Account $accountId is missing" }
            expireOldPending(account)

            val posts = store.postsOf(accountId)
            val release = dueRelease(account, posts)
            val now = clock.instant()
            val plans = WeekPlanner.plan(
                zone = account.zone,
                weekStart = monday,
                now = now,
                publishedCount = account.publishedCount,
                // The week's own batch posts do not count: the template is planned as the first run saw it, and the markers skip the slots that are written.
                occupied = occupiedSlots(posts.filter { it.batchWeek != week }),
                releaseDue = release != null,
            ).filter { it.scheduledAt > now }
            val markers = store.slotMarkers(week)
            // A rerun retries the slots that failed before, so the failure list starts empty.
            touch { it.copy(failedSlots = emptyList()) }

            val tipTimes = (
                posts.filter { it.batchWeek == week && it.source == PostSource.Drafter && it.pillar == Pillar.Tip }.mapNotNull { it.scheduledAt } +
                    plans.filter { it.pillar == Pillar.Tip }.map { it.scheduledAt }
                ).toSortedSet()
            val recent = posts.sortedByDescending { it.scheduledAt ?: it.createdAt }.map { it.text }.toMutableList()
            val failed = mutableListOf<String>()
            var written = 0
            var seq = account.postSeq

            for (plan in plans) {
                val slotId = "${plan.date.dayOfWeek.value}-${plan.slot}"
                val key = "${accountId}_$slotId"
                if (key in markers) continue

                val brief = SlotBrief(
                    pillar = plan.pillar,
                    language = account.language,
                    recentTexts = recent.take(Prompts.RECENT_POSTS),
                    release = release.takeIf { plan.pillar == Pillar.Update },
                    linkReplyAllowed = plan.linkReplyAllowed,
                    // Every second tip in time order may name the app; the count includes the tips already written.
                    mayMentionApp = tipTimes.headSet(plan.scheduledAt).size % 2 == 1,
                )
                val linkUrl = previews.linkUrl(PostCode.format(account.codePrefix, plan.pillar, seq + 1), account.language)
                when (val outcome = pipeline.run(brief, account.publishedCount, linkUrl)) {
                    is DraftOutcome.Draft -> {
                        val post = writeSlot(key, plan, outcome, release)
                        if (post != null) {
                            written++
                            seq++
                            recent.add(0, post.text)
                        }
                    }
                    is DraftOutcome.DraftFailed -> {
                        failed += slotId
                        touch { it.copy(failedSlots = failed.toList()) }
                    }
                    is DraftOutcome.UsageStop -> return deferred(written, failed, "the daily AI call limit is reached")
                    is DraftOutcome.Deferred -> return deferred(written, failed, outcome.reason)
                }
            }

            sendPreviews(account, failed)
            touch { it.copy(done = true) }
            return AccountReport(accountId, AccountStatus.Done, written, failed)
        }

        private suspend fun deferred(written: Int, failed: List<String>, reason: String): AccountReport {
            log.warn("Batch {} stopped drafting {}: {}", week, accountId, reason)
            quietly { alerts.sendOnce(AlertRequest(AlertType.BatchDeferred, accountId, detail = reason, week = week)) }
            return AccountReport(accountId, AccountStatus.Deferred, written, failed, reason)
        }

        /** Every non-owner pending post whose slot lies before the target Monday becomes `skipped`, reason `expired`. */
        private suspend fun expireOldPending(account: SocialAccount) {
            val cutoff = monday.atStartOfDay(account.zone).toInstant()
            val stale = store.postsOf(accountId).filter {
                it.source != PostSource.Owner && it.status == PostStatus.Pending && it.scheduledAt?.isBefore(cutoff) == true
            }
            for (post in stale) {
                val skipped = store.transitionPost(post.id, PostStatus.Pending) {
                    it.copy(status = PostStatus.Skipped, skipReason = SKIP_REASON_EXPIRED, editPromptMessageId = null)
                } ?: continue
                quietly { sender.refresh(skipped) }
            }
        }

        /** The newest active release that no update post of the account announced yet and that is at most 30 days old. */
        private suspend fun dueRelease(account: SocialAccount, posts: List<SocialPost>): ReleaseNotes? {
            val announced = posts.filter { it.pillar == Pillar.Update }.mapNotNull { it.sourceRef }.toSet()
            val today = clock.instant().atZone(account.zone).toLocalDate()
            return releases.activeReleases().sortedByDescending { it.date }.firstOrNull { release ->
                val date = try {
                    LocalDate.parse(release.date)
                } catch (e: DateTimeException) {
                    return@firstOrNull false
                }
                "$RELEASE_REF${release.version}" !in announced && !date.isAfter(today) && !date.isBefore(today.minusDays(RELEASE_DAYS))
            }
        }

        /**
         * Creates the slot marker, the post and the `postSeq` increment in one transaction and renews the lease in it.
         * Returns null when the marker already exists, so a second run writes nothing.
         */
        private suspend fun writeSlot(key: String, plan: WeekPlanner.SlotPlan, draft: DraftOutcome.Draft, release: ReleaseNotes?): SocialPost? =
            store.transaction { tx ->
                val batch = tx.batch(week)
                val marker = tx.slotMarker(week, key)
                val account = checkNotNull(tx.account(accountId)) { "Account $accountId is missing" }
                if (batch == null || batch.runId != runId) throw LeaseLost()
                if (marker != null) return@transaction null

                val now = clock.instant()
                val seq = account.postSeq + 1
                val code = PostCode.format(account.codePrefix, plan.pillar, seq)
                val auto = account.autoApproves(draft.needsAttention)
                val post = SocialPost(
                    id = code,
                    accountId = accountId,
                    code = code,
                    pillar = plan.pillar,
                    kind = PostKind.Text,
                    text = draft.text,
                    replyText = draft.replyText,
                    link = ContentRules.LINK_PLACEHOLDER in draft.text,
                    source = PostSource.Drafter,
                    scheduledAt = plan.scheduledAt,
                    status = if (auto) PostStatus.Approved else PostStatus.Pending,
                    needsAttention = draft.needsAttention,
                    autoApproved = auto,
                    backTranslation = draft.backTranslation,
                    checkNotes = draft.checkNotes,
                    sourceRef = release?.takeIf { plan.pillar == Pillar.Update }?.let { "$RELEASE_REF${it.version}" },
                    batchWeek = week,
                    createdAt = now,
                )
                val entry = batch.accounts[accountId] ?: BatchAccount()
                tx.putSlotMarker(week, key, code)
                tx.putPost(post)
                tx.putAccount(account.copy(postSeq = seq))
                tx.putBatch(
                    batch.copy(
                        leaseUntil = now.plus(LEASE),
                        accounts = batch.accounts + (accountId to entry.copy(slotsWritten = entry.slotsWritten + 1)),
                    ),
                )
                post
            }

        /** Renews the lease and changes the account's entry in the batch document; throws [LeaseLost] when the run no longer owns it. */
        private suspend fun touch(change: (BatchAccount) -> BatchAccount = { it }) {
            store.transaction { tx ->
                val batch = tx.batch(week)
                if (batch == null || batch.runId != runId) throw LeaseLost()
                val entry = batch.accounts[accountId] ?: BatchAccount()
                tx.putBatch(
                    batch.copy(
                        leaseUntil = clock.instant().plus(LEASE),
                        accounts = batch.accounts + (accountId to change(entry)),
                    ),
                )
            }
        }

        /**
         * One header, then the preview of every batch post that has none, in time order, then one closing line.
         * Telegram failures are skipped one by one: the tick sends a preview that is still missing once the lease is released.
         */
        private suspend fun sendPreviews(account: SocialAccount, failed: List<String>) {
            val owned = store.postsOf(accountId)
                .filter { it.batchWeek == week && it.status in PREVIEWED }
                .sortedBy { it.scheduledAt }
            val unsent = owned.filter { it.telegramMessageId == null }
            if (unsent.isNotEmpty()) {
                quietly { telegram.sendMessage(ownerChatId, BotText.batchHeader(account.handle, week, owned.size)) }
                for (post in unsent) {
                    quietly { sender.send(post) }
                    touch()
                }
            }
            quietly { telegram.sendMessage(ownerChatId, BotText.batchClosing(failed, usage.callsToday())) }
        }
    }

    /** Runs a Telegram step; a [TelegramFailure] is logged and dropped. */
    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TelegramFailure) {
            log.warn("A batch message was not delivered: {}", redact(e.message.orEmpty()).take(MAX_DETAIL))
        }
    }

    private companion object {
        const val MAX_PARALLEL = 3
        const val RELEASE_REF = "release:"
        const val RELEASE_DAYS = 30L
        const val MAX_DETAIL = 200
        val LEASE: Duration = Duration.ofMinutes(3)
        val PREVIEWED = setOf(PostStatus.Pending, PostStatus.Approved)
    }
}
