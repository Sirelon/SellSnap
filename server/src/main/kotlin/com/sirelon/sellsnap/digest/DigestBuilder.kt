package com.sirelon.sellsnap.digest

import com.sirelon.sellsnap.model.AccountMetrics
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostMetrics
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialClick
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.telegram.BotText
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Builds one account's Sunday digest (design §7): a plain-text English message from what the store holds. The
 * window is the 7 days before the run. It reads only the store, so the numbers are the ones the tick, the click
 * report, the install sync and the usage counters already wrote; a caller that wants fresh Threads numbers
 * collects them first. The only field that may say "n/a" is the follower change of a week with no 7-day-old snapshot.
 */
class DigestBuilder(
    private val store: SocialStore,
    private val clock: Clock,
) {
    /** [dailyCallLimit] is the free-tier call limit per model and UTC day that the usage line is read against. */
    suspend fun build(account: SocialAccount, dailyCallLimit: Int): String {
        val now = clock.instant()
        val from = now.minus(WINDOW)
        val zone = account.zone
        val today = now.atZone(zone).toLocalDate()
        val windowStart = from.atZone(zone).toLocalDate()

        val published = store.postsOf(account.id).filter {
            it.status == PostStatus.Published && it.publishedAt?.let { at -> !at.isBefore(from) && !at.isAfter(now) } == true
        }
        val snapshots = latestSnapshots(account.id, windowStart, today)
        val clicks = store.clicksSince(from).filter { !it.ts.isAfter(now) }.groupBy { it.code }

        val lines = mutableListOf<String>()
        lines += BotText.digestHeader(account.handle, windowStart, today)
        lines += ""
        lines += followerLine(account.id, today)
        lines += postsLines(published, snapshots, clicks)
        lines += ""
        lines += installs(account, windowStart)
        lines += eventsLine(account, from, now)
        lines += usageLine(dailyCallLimit)
        return lines.joinToString("\n").take(MAX_LENGTH)
    }

    /** The follower change from the snapshot of today and the newest snapshot at least 7 days old. */
    private suspend fun followerLine(accountId: String, today: LocalDate): String {
        val all = store.accountMetricsOf(accountId).sortedBy { it.day }
        val current = all.lastOrNull { it.day == DAY.format(today) }?.followersCount
            ?: return if (all.isEmpty()) BotText.DIGEST_NO_FOLLOWER_SNAPSHOT else BotText.DIGEST_FOLLOWERS_UNAVAILABLE
        val baseline: AccountMetrics? = all.lastOrNull { it.followersCount != null && LocalDate.parse(it.day, DAY) <= today.minusDays(WINDOW_DAYS) }
        if (baseline == null) {
            val first = all.firstOrNull { it.followersCount != null } ?: all.first()
            return BotText.digestFollowerFirstSnapshot(LocalDate.parse(first.day, DAY), current)
        }
        return BotText.digestFollowerChange(current - baseline.followersCount!!, LocalDate.parse(baseline.day, DAY), current)
    }

    /** The newest snapshot of every post, read from the account's days between [from] and [to]. */
    private suspend fun latestSnapshots(accountId: String, from: LocalDate, to: LocalDate): Map<String, PostMetrics> {
        val latest = mutableMapOf<String, PostMetrics>()
        var day = from
        while (!day.isAfter(to)) {
            for (metrics in store.metricsOf(accountId, DAY.format(day))) {
                val known = latest[metrics.postId]
                if (known == null || metrics.day > known.day) latest[metrics.postId] = metrics
            }
            day = day.plusDays(1)
        }
        return latest
    }

    private fun postsLines(published: List<SocialPost>, snapshots: Map<String, PostMetrics>, clicks: Map<String, List<SocialClick>>): List<String> {
        val measured = published.mapNotNull { post -> snapshots[post.id]?.let { post to it } }
        val viewsMissing = measured.count { it.second.views == null }
        val lines = mutableListOf(
            BotText.digestPosts(published.size, measured.sumOf { it.second.views ?: 0 }, viewsMissing, measured.sumOf { it.second.replies }),
        )
        val ownClicks = published.flatMap { clicks[it.code].orEmpty() }
        lines += BotText.digestClicks(ownClicks.count(LANDING), ownClicks.count(PLAY), ownClicks.count(APP_STORE))
        if (measured.isEmpty()) return lines

        val byViews = viewsMissing == 0
        fun score(metrics: PostMetrics): Long = if (byViews) metrics.views ?: 0 else metrics.likes + metrics.replies
        val ranked = measured.sortedWith(compareByDescending<Pair<SocialPost, PostMetrics>> { score(it.second) }.thenBy { it.first.code })
        val top = ranked.take(RANKED)
        val bottom = ranked.drop(top.size).takeLast(RANKED).reversed()

        fun section(top: Boolean, entries: List<Pair<SocialPost, PostMetrics>>) {
            if (entries.isEmpty()) return
            lines += ""
            lines += BotText.digestRankingTitle(top, entries.size, byViews)
            entries.forEachIndexed { index, (post, metrics) ->
                val own = clicks[post.code].orEmpty()
                lines += BotText.digestPostLine(
                    rank = index + 1,
                    code = post.code,
                    start = textStart(post.text),
                    figures = BotText.digestFigures(byViews, metrics.views, metrics.likes, metrics.replies),
                    landing = own.count(LANDING),
                    play = own.count(PLAY),
                    appStore = own.count(APP_STORE),
                )
            }
        }
        section(top = true, entries = top)
        section(top = false, entries = bottom)
        return lines
    }

    private suspend fun installs(account: SocialAccount, windowStart: LocalDate): String {
        val inWindow = store.installsOf(account.id).filter { it.day >= DAY.format(windowStart) }
        val byCode = inWindow.groupBy { it.code }.mapValues { (_, rows) -> rows.sumOf { it.installs } }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(RANKED).map { it.key to it.value }
        return BotText.digestInstalls(inWindow.sumOf { it.installs }, byCode)
    }

    private suspend fun eventsLine(account: SocialAccount, from: Instant, now: Instant): String {
        val events = store.eventsSince(from).filter { it.accountId == account.id && !it.at.isAfter(now) }
        fun count(type: EventType) = events.count { it.type == type }
        return BotText.digestEvents(count(EventType.Error), count(EventType.Pause), count(EventType.Resume), count(EventType.ModeChange), count(EventType.Delete))
    }

    /** Calls and tokens of each model over the last 7 UTC days, with the busiest day's calls against the daily limit. */
    private suspend fun usageLine(dailyCallLimit: Int): String {
        val today = clock.instant().atOffset(ZoneOffset.UTC).toLocalDate()
        val perDay = (0 until WINDOW_DAYS).map { store.usageOf(DAY.format(today.minusDays(it))) }
        val models = perDay.flatMap { it.keys }.toSortedSet()
        if (models.isEmpty()) return BotText.DIGEST_NO_USAGE
        return BotText.digestUsage(
            models.map { model ->
                val days = perDay.mapNotNull { it[model] }
                BotText.digestModel(
                    model = model,
                    calls = days.sumOf { it.calls },
                    tokens = days.sumOf { it.promptTokens + it.outputTokens + it.thoughtsTokens },
                    peakCalls = days.maxOf { it.calls },
                    dailyLimit = dailyCallLimit,
                )
            },
        )
    }

    private fun textStart(text: String): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= START_LENGTH) flat else flat.take(START_LENGTH).trimEnd() + "…"
    }

    private companion object {
        const val RANKED = 3
        const val START_LENGTH = 40
        const val MAX_LENGTH = 4096
        const val WINDOW_DAYS = 7L
        val WINDOW: Duration = Duration.ofDays(WINDOW_DAYS)
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val LANDING: (SocialClick) -> Boolean = { it.store == "landing" }
        val PLAY: (SocialClick) -> Boolean = { it.store == "play" }
        val APP_STORE: (SocialClick) -> Boolean = { it.store == "appstore" }
    }
}
