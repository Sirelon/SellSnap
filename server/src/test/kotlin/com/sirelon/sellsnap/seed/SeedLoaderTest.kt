package com.sirelon.sellsnap.seed

import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import kotlinx.coroutines.test.runTest
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SeedLoaderTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T05:00:00Z"))
    private val store = InMemorySocialStore()
    private val loader = SeedLoader(store, clock)

    private val accountsJson = File("seed/accounts.json").readText()
    private val sampleTips = checkNotNull(javaClass.getResource("/seed/tips-sample.json")).readText()

    private fun tips(vararg entries: Pair<String, String>) =
        """{"uk":[${entries.joinToString(",") { (key, text) -> """{"key":"$key","text":${quote(text)}}""" }}]}"""

    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun theCommittedAccountsFileLoadsTheUkrainianAndPolishAccounts() = runTest {
        assertEquals(listOf("ua", "pl"), loader.upsertAccounts(accountsJson))

        val ua = store.allAccounts.getValue("ua")
        assertEquals(listOf("sellsnap.app", "uk", "UA", "Europe/Kyiv", "uk"), listOf(ua.handle, ua.language, ua.country, ua.timezone, ua.codePrefix))
        val pl = store.allAccounts.getValue("pl")
        assertEquals(listOf("sellsnap.pl", "pl", "PL", "Europe/Warsaw", "pl"), listOf(pl.handle, pl.language, pl.country, pl.timezone, pl.codePrefix))
        assertEquals(AccountMode.Approval, ua.mode)
    }

    @Test
    fun upsertKeepsTheTokenModeStreakAndCounters() = runTest {
        val live = testAccount(id = "ua", codePrefix = "uk").copy(
            handle = "old.handle",
            mode = AccountMode.Auto,
            approvalStreak = 20,
            token = "stored-token",
            threadsUserId = "threads-user",
            tokenExpiresAt = Instant.parse("2026-12-01T00:00:00Z"),
            postSeq = 7,
            publishedCount = 12,
            paused = true,
            pausedReason = "code 190",
        )
        store.put(live)

        loader.upsertAccounts(accountsJson)

        val after = store.allAccounts.getValue("ua")
        assertEquals(live.copy(handle = "sellsnap.app"), after)
    }

    @Test
    fun tipsBecomeDraftedTipPostsWithCodesFromThePostSequence() = runTest {
        loader.upsertAccounts(accountsJson)
        val result = loader.loadTips("ua", "uk", sampleTips)

        assertEquals(TipsResult.Loaded(listOf("ukt0001", "ukt0002"), emptyList()), result)
        val first = store.allPosts.getValue("ukt0001")
        assertEquals("sample01", first.seedKey)
        assertEquals(PostStatus.Drafted, first.status)
        assertEquals(Pillar.Tip, first.pillar)
        assertEquals(PostKind.Text, first.kind)
        assertEquals(PostSource.Seed, first.source)
        assertEquals("ua", first.accountId)
        assertEquals(2, store.allAccounts.getValue("ua").postSeq)
    }

    @Test
    fun loadingTwiceAddsNothing() = runTest {
        loader.upsertAccounts(accountsJson)
        loader.loadTips("ua", "uk", sampleTips)
        val second = loader.loadTips("ua", "uk", sampleTips)

        assertEquals(TipsResult.Loaded(emptyList(), listOf("sample01", "sample02")), second)
        assertEquals(2, store.allPosts.size)
        assertEquals(2, store.allAccounts.getValue("ua").postSeq)
    }

    @Test
    fun aNewTipInALaterLoadContinuesTheSequence() = runTest {
        loader.upsertAccounts(accountsJson)
        loader.loadTips("ua", "uk", sampleTips)
        val result = loader.loadTips("ua", "uk", tips("sample01" to "Placeholder tip one. This text only shows the file format.", "sample03" to "Placeholder tip three."))

        assertEquals(TipsResult.Loaded(listOf("ukt0003"), listOf("sample01")), result)
        assertEquals("sample03", store.allPosts.getValue("ukt0003").seedKey)
    }

    @Test
    fun twoAccountsMayShareAKeyAndTheirCodesUseTheirOwnPrefix() = runTest {
        loader.upsertAccounts(accountsJson)
        loader.loadTips("ua", "uk", sampleTips)
        val polish = tips("sample01" to "Placeholder tip one.", "sample02" to "Placeholder tip two.").replace("\"uk\"", "\"pl\"")
        val result = loader.loadTips("pl", "pl", polish)
        assertEquals(TipsResult.Loaded(listOf("plt0001", "plt0002"), emptyList()), result)
    }

    @Test
    fun aViolationMeansNothingIsWrittenAndNamesTheTip() = runTest {
        loader.upsertAccounts(accountsJson)
        val bad = tips(
            "ok01" to "A fine placeholder tip.",
            "link01" to "A tip with {link} in it.",
            "url01" to "A tip with https://example.com in it.",
            "long01" to "x".repeat(600),
            "ru01" to "A tip with a ${Char(0x44B)} letter.",
            "empty01" to " ",
        )

        val result = loader.loadTips("ua", "uk", bad)

        val rejected = result as TipsResult.Rejected
        assertEquals(setOf("link01", "url01", "long01", "ru01", "empty01"), rejected.problems.keys)
        assertTrue("LinkNotAllowed" in rejected.problems.getValue("link01"))
        assertTrue("RawUrl" in rejected.problems.getValue("url01"))
        assertTrue("TooLong" in rejected.problems.getValue("long01"))
        assertTrue("RussianLetter" in rejected.problems.getValue("ru01"))
        assertTrue(store.allPosts.isEmpty())
        assertEquals(0, store.allAccounts.getValue("ua").postSeq)
    }

    @Test
    fun aDuplicateKeyIsRejected() = runTest {
        loader.upsertAccounts(accountsJson)
        val result = loader.loadTips("ua", "uk", tips("same" to "One.", "same" to "Two."))
        assertEquals(setOf("same"), (result as TipsResult.Rejected).problems.keys)
        assertTrue(store.allPosts.isEmpty())
    }

    @Test
    fun wrongAccountLanguageOrFileIsAnError() = runTest {
        loader.upsertAccounts(accountsJson)
        assertFailsWith<SeedException> { loader.loadTips("nobody", "uk", sampleTips) }
        assertFailsWith<SeedException> { loader.loadTips("ua", "pl", sampleTips) }
        assertFailsWith<SeedException> { loader.loadTips("pl", "pl", tips("a" to "b")) }
        assertFailsWith<SeedException> { loader.loadTips("ua", "uk", "{ not json") }
        assertTrue(store.allPosts.isEmpty())
    }

    @Test
    fun aSharedCodePrefixIsRefused() = runTest {
        val clash = """[{"id":"a","handle":"h","language":"uk","country":"UA","timezone":"Europe/Kyiv","codePrefix":"st"},
            {"id":"b","handle":"h2","language":"uk","country":"UA","timezone":"Europe/Kyiv","codePrefix":"st"}]"""
        assertFailsWith<SeedException> { loader.upsertAccounts(clash) }
        assertTrue(store.allAccounts.isEmpty())
    }
}
