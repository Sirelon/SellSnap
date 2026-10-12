package com.sirelon.sellsnap.digest

import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.model.AccountMetrics
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostMetrics
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialClick
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.model.SocialInstall
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.model.UsageCounters
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigestBuilderTest {
    private val store = InMemorySocialStore()
    private val clock = MutableClock(RUN)
    private val builder = DigestBuilder(store, clock)
    private val ua: SocialAccount = testAccount(id = "ua", codePrefix = "uk", language = "uk", timezone = "Europe/Kyiv")

    private fun published(code: String, text: String, at: String): SocialPost =
        testPost(code, "ua", PostStatus.Published).copy(text = text, publishedAt = Instant.parse(at)).also { store.put(it) }

    private suspend fun snapshot(post: SocialPost, day: String, views: Long?, likes: Long, replies: Long) =
        store.saveMetrics(PostMetrics(post.id, "ua", day, Instant.parse("2026-10-18T05:00:00Z"), views, likes, replies, 0, 0))

    private suspend fun followers(day: String, count: Long?) =
        store.saveAccountMetrics(AccountMetrics("ua", day, Instant.parse("2026-10-18T05:00:00Z"), count))

    private fun click(code: String, storeName: String, at: String, n: Int) =
        repeat(n) { store.put(SocialClick("$code-$storeName-$at-$it", code, Instant.parse(at), storeName, "android")) }

    private fun event(id: String, account: String, type: EventType, at: String) =
        store.put(SocialEvent(id, account, type, Instant.parse(at)))

    private suspend fun usage(day: String, model: String, calls: Long, prompt: Long, output: Long, thoughts: Long) =
        store.addUsage(day, model, UsageCounters(calls, prompt, output, thoughts))

    /** Five posts in the window, one before it, one pending; the rest of the week's data. */
    private suspend fun week(baseline: Boolean = true) {
        val a = published("ukt0001", "Photograph the label too, buyers ask for it", "2026-10-12T05:30:00Z")
        val b = published("ukt0002", "Wipe the item first.\nA clean item sells faster.", "2026-10-13T05:30:00Z")
        val c = published("ukd0003", "Try SellSnap", "2026-10-14T05:30:00Z")
        val d = published("ukt0004", "Daylight beats a lamp", "2026-10-15T05:30:00Z")
        val e = published("uku0005", "Version 3.3 keeps your drafts", "2026-10-16T05:30:00Z")
        published("ukt0000", "Too old", "2026-10-10T05:30:00Z")
        store.put(testPost("ukt0006", "ua", PostStatus.Pending))
        for ((post, numbers) in listOf(a to Triple(900L, 40L, 6L), b to Triple(120L, 3L, 0L), c to Triple(450L, 20L, 4L), d to Triple(300L, 10L, 1L), e to Triple(60L, 2L, 0L))) {
            // An older snapshot of the same post must lose against the newer one.
            snapshot(post, "20261015", numbers.first / 2, numbers.second / 2, numbers.third / 2)
            snapshot(post, "20261018", numbers.first, numbers.second, numbers.third)
        }
        // Another account's post and snapshot never enter the totals.
        store.put(testPost("plt0001", "pl", PostStatus.Published).copy(publishedAt = Instant.parse("2026-10-13T05:30:00Z")))

        if (baseline) followers("20261011", 1000)
        followers("20261014", 1010)
        followers("20261018", 1036)

        click("ukt0001", "landing", "2026-10-12T10:00:00Z", 12)
        click("ukt0001", "play", "2026-10-12T10:05:00Z", 4)
        click("ukt0001", "appstore", "2026-10-12T10:06:00Z", 3)
        click("ukd0003", "landing", "2026-10-15T10:00:00Z", 2)
        click("ukd0003", "play", "2026-10-15T10:01:00Z", 1)
        click("ukt0002", "landing", "2026-10-13T10:00:00Z", 1)
        click("ukt0001", "landing", "2026-10-09T10:00:00Z", 50)
        click("plt0001", "play", "2026-10-13T10:00:00Z", 7)

        store.saveInstall(SocialInstall("ua", "ukt0001", "20261014", 2, RUN))
        store.saveInstall(SocialInstall("ua", "uku0005", "20261016", 1, RUN))
        store.saveInstall(SocialInstall("ua", "ukt0001", "20261001", 9, RUN))
        store.saveInstall(SocialInstall("pl", "plt0001", "20261014", 5, RUN))

        event("e1", "ua", EventType.Error, "2026-10-12T09:00:00Z")
        event("e2", "ua", EventType.Error, "2026-10-13T09:00:00Z")
        event("e3", "ua", EventType.Pause, "2026-10-13T09:01:00Z")
        event("e4", "ua", EventType.Resume, "2026-10-13T10:00:00Z")
        event("e5", "ua", EventType.ModeChange, "2026-10-14T10:00:00Z")
        event("e6", "ua", EventType.Delete, "2026-10-15T10:00:00Z")
        event("e7", "ua", EventType.Alert, "2026-10-15T11:00:00Z")
        event("e8", "ua", EventType.Error, "2026-10-09T09:00:00Z")
        event("e9", "pl", EventType.Pause, "2026-10-13T09:00:00Z")

        usage("20261018", "gemini-3.8-flash", 24, 40_000, 8_000, 12_000)
        usage("20261017", "gemini-3.8-flash", 60, 90_000, 20_000, 30_000)
        usage("20261014", "gemini-3.5-flash-lite", 12, 9_000, 1_500, 0)
        usage("20261010", "gemini-3.8-flash", 150, 1, 1, 1)
    }

    @Test
    fun everyFieldComesFromTheStoreAndTheWindowIsTheSevenDaysBeforeTheRun() = runTest {
        week()

        val text = builder.build(ua, dailyCallLimit = 200)

        assertEquals(
            """
            @sellsnap.ua, digest for 11 Oct 2026 to 18 Oct 2026

            Follower change: +36 since 11 Oct 2026 (now 1036).
            Posts published: 5. Views: 1830. Replies: 11.
            Clicks on all posts: landing 15, play 5, appstore 3.

            Top 3 by views:
            1. ukt0001 "Photograph the label too, buyers ask for…" - 900 views, 6 replies; clicks: landing 12, play 4, appstore 3.
            2. ukd0003 "Try SellSnap" - 450 views, 4 replies; clicks: landing 2, play 1, appstore 0.
            3. ukt0004 "Daylight beats a lamp" - 300 views, 1 reply; clicks: landing 0, play 0, appstore 0.

            Bottom 2 by views:
            1. uku0005 "Version 3.3 keeps your drafts" - 60 views, 0 replies; clicks: landing 0, play 0, appstore 0.
            2. ukt0002 "Wipe the item first. A clean item sells…" - 120 views, 0 replies; clicks: landing 1, play 0, appstore 0.

            Android installs from Threads: 3 (ukt0001: 2, uku0005: 1).
            Events: errors 2, pauses 1, resumes 1, mode changes 1, deletes 1.
            Model use, last 7 UTC days, all accounts: gemini-3.5-flash-lite: 12 calls, 10500 tokens, busiest day 12 of 200 calls; gemini-3.8-flash: 84 calls, 200000 tokens, busiest day 60 of 200 calls.
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun theFirstWeekHasNoSevenDayOldSnapshotAndOnlyTheFollowerChangeSaysNotAvailable() = runTest {
        // Only snapshots younger than seven days exist; the first was taken on the 14th.
        week(baseline = false)

        val text = builder.build(ua, dailyCallLimit = 200)

        assertTrue("Follower change: n/a (first snapshot on 14 Oct 2026). Followers now: 1036." in text, text)
        assertEquals(1, Regex("n/a").findAll(text).count(), "no other field says n/a")
    }

    @Test
    fun withoutAnyViewsFigureThePostsAreRankedByLikesPlusRepliesAndSaySo() = runTest {
        val a = published("ukt0001", "A", "2026-10-12T05:30:00Z")
        val b = published("ukt0002", "B", "2026-10-13T05:30:00Z")
        val c = published("ukt0003", "C", "2026-10-14T05:30:00Z")
        val d = published("ukt0004", "D", "2026-10-15T05:30:00Z")
        snapshot(a, "20261018", views = null, likes = 5, replies = 1)
        snapshot(b, "20261018", views = null, likes = 1, replies = 9)
        snapshot(c, "20261018", views = 4000, likes = 2, replies = 0)
        snapshot(d, "20261018", views = null, likes = 0, replies = 0)

        val text = builder.build(ua, dailyCallLimit = 200)

        assertTrue("Posts published: 4. Views: 4000 (3 without a views figure). Replies: 10." in text, text)
        assertTrue("Top 3 by likes + replies (views unavailable):" in text)
        val ranking = text.lines().filter { Regex("^\\d\\. ukt").containsMatchIn(it) }.map { it.substringAfter(". ").substringBefore(' ') }
        assertEquals(listOf("ukt0002", "ukt0001", "ukt0003", "ukt0004"), ranking, "ranked 10, 6, 2, 0; the top three first, then the bottom one")
        assertTrue("\"B\" - 1 like, 9 replies" in text)
        assertFalse("Bottom 3" in text)
        assertTrue("Bottom 1 by likes + replies (views unavailable):" in text)
    }

    @Test
    fun anAccountWithNothingYetStillGetsEveryLine() = runTest {
        val text = builder.build(ua, dailyCallLimit = 200)

        assertEquals(
            """
            @sellsnap.ua, digest for 11 Oct 2026 to 18 Oct 2026

            Follower change: unavailable, there is no follower snapshot yet.
            Posts published: 0. Views: 0. Replies: 0.
            Clicks on all posts: landing 0, play 0, appstore 0.

            Android installs from Threads: 0.
            Events: errors 0, pauses 0, resumes 0, mode changes 0, deletes 0.
            Model use: no calls this week.
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun aFollowerCountThatThreadsDidNotReturnIsReportedAsUnavailableNotAsAChange() = runTest {
        followers("20261011", 1000)
        followers("20261018", null)

        val text = builder.build(ua, dailyCallLimit = 200)

        assertTrue("Follower change: unavailable, Threads returned no follower count today." in text)
    }

    @Test
    fun theMessageStaysUnderTheTelegramLimitEvenWithLongTextsAndManyModels() = runTest {
        repeat(40) { index ->
            val post = published("ukt%04d".format(index + 1), "A long tip ".repeat(60), "2026-10-14T05:30:00Z")
            snapshot(post, "20261018", views = index * 10L, likes = 1, replies = 1)
        }
        repeat(80) { usage("20261018", "gemini-model-with-a-rather-long-name-$it", 1, 1, 1, 1) }

        val text = builder.build(ua, dailyCallLimit = 200)

        assertTrue(text.length <= 4096, "length ${text.length}")
        assertTrue(text.startsWith("@sellsnap.ua, digest for"))
    }

    @Test
    fun theTypicalDigestIsFarUnderTheLimit() = runTest {
        week()

        assertTrue(builder.build(ua, dailyCallLimit = 200).length < 1500)
    }
}

private val RUN: Instant = Instant.parse("2026-10-18T06:00:00Z")
