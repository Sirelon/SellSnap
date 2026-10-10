package com.sirelon.sellsnap.tick

import com.sirelon.sellsnap.batch.IsoWeek
import com.sirelon.sellsnap.insights.InsightsCollector
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.telegram.PreviewSender
import com.sirelon.sellsnap.threads.AlertRequest
import com.sirelon.sellsnap.threads.AlertType
import com.sirelon.sellsnap.threads.PublishOutcome
import com.sirelon.sellsnap.threads.Publisher
import com.sirelon.sellsnap.threads.ReplyState
import com.sirelon.sellsnap.threads.TokenLifecycle
import com.sirelon.sellsnap.threads.redact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** What one tick did, for the response body and the logs. */
data class TickSummary(
    val accounts: Int = 0,
    val published: Int = 0,
    val queued: Int = 0,
    /** Post insight snapshots written. */
    val snapshots: Int = 0,
    val failedAccounts: Int = 0,
    val timedOut: Boolean = false,
) {
    /** True when every account ran to the end within the time limit. */
    val ok: Boolean get() = failedAccounts == 0 && !timedOut

    fun line(): String =
        "tick accounts=$accounts published=$published queued=$queued snapshots=$snapshots failed=$failedAccounts timedOut=$timedOut"
}

/**
 * One scheduler tick. Each active account runs the token check, then publishes what is due, then
 * fills its queue, so a refreshed token is the one used to publish. Every account with a token,
 * paused ones included, then gets its daily insight snapshot; a paused account gets nothing else.
 * The whole tick shares one [timeout]. A failure in one account is recorded and alerted, and the
 * other accounts still run; a failure in the snapshot step never fails the tick.
 */
class TickRunner(
    private val store: SocialStore,
    private val tokens: TokenLifecycle,
    private val publisher: Publisher,
    private val filler: QueueFiller,
    private val previewSender: PreviewSender,
    private val alerts: Alerts,
    private val insights: InsightsCollector,
    private val clock: Clock,
    private val timeout: Duration = 9.minutes,
) {
    private val log = LoggerFactory.getLogger(TickRunner::class.java)

    suspend fun run(): TickSummary {
        var summary = TickSummary()
        try {
            withTimeout(timeout) {
                for (account in store.accounts().sortedBy { it.id }) {
                    if (!account.paused) {
                        summary = summary.copy(accounts = summary.accounts + 1)
                        summary = try {
                            val step = runAccount(account.id)
                            summary.copy(published = summary.published + step.published, queued = summary.queued + step.queued)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            reportFailure(account.id, e)
                            summary.copy(failedAccounts = summary.failedAccounts + 1)
                        }
                    }
                    if (account.token != null) {
                        summary = summary.copy(snapshots = summary.snapshots + snapshot(account.id))
                    }
                }
                runQuietly { alertStalledBatches() }
            }
        } catch (e: TimeoutCancellationException) {
            log.error("Tick stopped at the {} time limit", timeout)
            summary = summary.copy(timedOut = true)
        }
        return summary
    }

    private class AccountStep(val published: Int, val queued: Int)

    private suspend fun runAccount(accountId: String): AccountStep {
        // The token check recorded its alerts already; the publisher's are recorded here. A Telegram
        // outage must not stop the account's publishing, so alert delivery is best effort.
        tokens.tick(accountId).alerts.forEach { runQuietly { alerts.send(it) } }

        val outcomes = publisher.publishDue(accountId)
        var published = 0
        for (outcome in outcomes) {
            outcome.alerts.forEach { runQuietly { alerts.sendOnce(it) } }
            if (outcome is PublishOutcome.Published) {
                published++
                store.post(outcome.postId)?.let { previewSender.refresh(it) }
            } else if (outcome is PublishOutcome.Replied && outcome.state == ReplyState.Failed) {
                // The post is marked "needs attention"; its preview shows that.
                store.post(outcome.postId)?.let { previewSender.refresh(it) }
            }
        }

        val fill = filler.fill(accountId)
        return AccountStep(published, fill.queued)
    }

    /**
     * Collects the account's insight snapshots and sends the alerts it asks for. It returns the
     * number of post snapshots written and never throws, so one account's failure stops nothing.
     */
    private suspend fun snapshot(accountId: String): Int =
        try {
            val outcome = insights.collect(accountId)
            outcome.alerts.forEach { runQuietly { alerts.sendOnce(it) } }
            outcome.posts
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportFailure(accountId, e)
            0
        }

    /**
     * After [STALLED_AFTER] Kyiv time, alerts once a day for every account that the current batch started and has
     * not finished while no run holds its lease: the run failed, stopped on a busy model or ran out of time.
     */
    private suspend fun alertStalledBatches() {
        val now = clock.instant()
        if (now.atZone(IsoWeek.KYIV).toLocalTime() < STALLED_AFTER) return
        val week = IsoWeek.key(IsoWeek.targetMonday(now))
        val batch = store.batch(week) ?: return
        if (batch.leaseHeld(now)) return
        batch.accounts.filterValues { !it.done }.keys.sorted().forEach { accountId ->
            alerts.sendOnce(AlertRequest(AlertType.BatchStalled, accountId, week = week))
        }
    }

    /** Records an Error event, logs one redacted line and alerts the owner; none of it may throw. */
    private suspend fun reportFailure(accountId: String, error: Exception) {
        val detail = redact(error.message ?: error::class.simpleName.orEmpty()).replace(Regex("\\s+"), " ").take(MAX_DETAIL_LENGTH)
        log.error("Tick failed for {}: {}: {}", accountId, error::class.simpleName, detail)
        val now = clock.instant()
        runQuietly {
            store.recordEvent(
                SocialEvent("error_tick_${accountId}_${now.toEpochMilli()}", accountId, EventType.Error, now, detail = detail),
            )
        }
        runQuietly { alerts.sendOnce(AlertRequest(AlertType.TickFailed, accountId, detail = detail)) }
    }

    private suspend fun runQuietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("A tick report step failed: {}", e::class.simpleName)
        }
    }

    private companion object {
        const val MAX_DETAIL_LENGTH = 200
        val STALLED_AFTER: LocalTime = LocalTime.of(11, 0)
    }
}
