package com.sirelon.sellsnap.insights

import com.sirelon.sellsnap.model.AccountMetrics
import com.sirelon.sellsnap.model.PostMetrics
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.threads.AlertRequest
import com.sirelon.sellsnap.threads.AlertType
import com.sirelon.sellsnap.threads.ThreadsApi
import com.sirelon.sellsnap.threads.ThreadsFailure
import com.sirelon.sellsnap.threads.redact
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What one collection did, for the tick summary; [alerts] go to `Alerts.sendOnce`. */
data class InsightsOutcome(
    /** Post snapshots written. */
    val posts: Int = 0,
    /** True when the account-day document was written, so the day counts as collected. */
    val collected: Boolean = false,
    /** True when the day was already collected and nothing was called. */
    val alreadyDone: Boolean = false,
    val alerts: List<AlertRequest> = emptyList(),
)

/**
 * Daily snapshots of Threads insights, once per account per local day: per-post counts for posts
 * published in the last [WINDOW_DAYS] days, and the follower count. Paused accounts are collected
 * too, and no failure here ever pauses an account: a permanent failure or a rate limit becomes an
 * [AlertType.InsightsFailed] request, deduplicated per day by `Alerts.sendOnce`, and a transient
 * failure is skipped silently.
 *
 * The day is marked collected by `claimAccountDay`, which is the last write: post snapshots are
 * idempotent overwrites of `{postId}_{day}`, and the follower count is fetched last, so a crash,
 * a transient failure or a rate limit before it leaves no marker and the next tick retries the
 * day. A permanent failure still marks the day (an unreachable follower count is stored as null),
 * so a missing permission is alerted once a day instead of being retried every tick.
 * Two overlapping runs may both call Threads; only one claim wins, and the snapshots are identical.
 */
class InsightsCollector(
    private val store: SocialStore,
    private val threads: ThreadsApi,
    private val clock: Clock,
) {
    /**
     * Collects [accountId]'s day. With [force] the day is collected again even when it was
     * already marked, and its documents are overwritten; the digest uses it to read fresh numbers.
     */
    suspend fun collect(accountId: String, force: Boolean = false): InsightsOutcome {
        val account = store.account(accountId) ?: return InsightsOutcome()
        val token = account.token ?: return InsightsOutcome()
        val userId = account.threadsUserId ?: return InsightsOutcome()
        val day = DAY.format(LocalDate.ofInstant(clock.instant(), account.zone))
        if (!force && store.accountMetrics(accountId, day) != null) return InsightsOutcome(alreadyDone = true)

        val run = Run(account, token, day)
        for (post in eligiblePosts(account)) {
            run.collectPost(post)
            if (run.rateLimited) break
        }
        if (!run.rateLimited) run.collectFollowers(userId)
        if (run.rateLimited || run.transientFailure) return InsightsOutcome(run.snapshots, alerts = run.alerts)

        val marker = AccountMetrics(accountId, day, clock.instant(), run.followers)
        if (force) {
            store.saveAccountMetrics(marker)
        } else {
            store.claimAccountDay(marker)
        }
        return InsightsOutcome(run.snapshots, collected = true, alerts = run.alerts)
    }

    private suspend fun eligiblePosts(account: SocialAccount): List<SocialPost> {
        val cutoff = clock.instant().minus(Duration.ofDays(WINDOW_DAYS))
        return store.postsOf(account.id)
            .filter { it.status == PostStatus.Published && it.mediaId != null && it.publishedAt?.isAfter(cutoff) == true }
            .sortedBy { it.publishedAt }
    }

    private inner class Run(private val account: SocialAccount, private val token: String, private val day: String) {
        var snapshots = 0
        var followers: Long? = null
        var rateLimited = false
        var transientFailure = false
        val alerts = mutableListOf<AlertRequest>()

        /** Reads one post's counts; `views` is its own call so its failure keeps the other metrics. */
        suspend fun collectPost(post: SocialPost) {
            val mediaId = post.mediaId ?: return
            val core = attempt(post.code) { threads.mediaInsights(mediaId, token, CORE_METRICS) } ?: return
            val views = if (rateLimited) null else optionalViews(mediaId)
            store.saveMetrics(
                PostMetrics(
                    postId = post.id,
                    accountId = account.id,
                    day = day,
                    at = clock.instant(),
                    views = views,
                    likes = core[LIKES] ?: 0,
                    replies = core[REPLIES] ?: 0,
                    reposts = core[REPOSTS] ?: 0,
                    quotes = core[QUOTES] ?: 0,
                ),
            )
            snapshots++
        }

        /**
         * `views` is "in development" on Threads, so it is best effort: any failure leaves it null
         * without an alert or a retry, and the digest ranks by likes and replies instead. Only a
         * rate limit still stops the run, because the next call would fail the same way.
         */
        private suspend fun optionalViews(mediaId: String): Long? =
            try {
                threads.mediaInsights(mediaId, token, listOf(VIEWS))[VIEWS]
            } catch (failure: ThreadsFailure) {
                if (failure is ThreadsFailure.RateLimited) rateLimited = true
                null
            }

        suspend fun collectFollowers(userId: String) {
            followers = attempt("followers") { threads.followersCount(userId, token) }
        }

        /** Runs [call]; a [ThreadsFailure] is recorded by its kind and becomes null, anything else propagates. */
        private suspend fun <T> attempt(what: String, call: suspend () -> T): T? =
            try {
                call()
            } catch (failure: ThreadsFailure) {
                when (failure) {
                    is ThreadsFailure.Transient -> transientFailure = true
                    is ThreadsFailure.RateLimited -> {
                        rateLimited = true
                        alert(what, failure)
                    }
                    is ThreadsFailure.Permanent -> alert(what, failure)
                }
                null
            }

        private fun alert(what: String, failure: ThreadsFailure) {
            if (alerts.isEmpty()) {
                alerts += AlertRequest(AlertType.InsightsFailed, account.id, detail = redact("$what: ${failure.message}"))
            }
        }
    }

    private companion object {
        const val WINDOW_DAYS = 14L
        const val VIEWS = "views"
        const val LIKES = "likes"
        const val REPLIES = "replies"
        const val REPOSTS = "reposts"
        const val QUOTES = "quotes"
        val CORE_METRICS = listOf(LIKES, REPLIES, REPOSTS, QUOTES)
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
