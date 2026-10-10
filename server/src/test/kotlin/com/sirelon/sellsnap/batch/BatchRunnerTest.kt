package com.sirelon.sellsnap.batch

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.brand.BrandContext
import com.sirelon.sellsnap.brand.ReleaseChange
import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.draft.DraftChecker
import com.sirelon.sellsnap.draft.DraftPipeline
import com.sirelon.sellsnap.draft.Drafter
import com.sirelon.sellsnap.fakes.DraftingLlm
import com.sirelon.sellsnap.fakes.FakeReleaseNotesSource
import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.testAccount
import com.sirelon.sellsnap.fakes.testPost
import com.sirelon.sellsnap.llm.LlmClient
import com.sirelon.sellsnap.llm.LlmRequest
import com.sirelon.sellsnap.llm.LlmResult
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.BatchAccount
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialBatch
import com.sirelon.sellsnap.spend.UsageTracker
import com.sirelon.sellsnap.telegram.InlineKeyboardMarkup
import com.sirelon.sellsnap.telegram.PreviewSender
import com.sirelon.sellsnap.telegram.Previews
import com.sirelon.sellsnap.tick.Alerts
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class BatchRunnerTest {
    private val llm = DraftingLlm()

    private fun fixture(releases: List<ReleaseNotes> = emptyList()) = AgentFixture(SUNDAY, llm = llm.client, releases = releases)

    private fun AgentFixture.ua(change: (SocialAccount) -> SocialAccount = { it }) =
        memory.put(change(publishable(testAccount(id = "ua", codePrefix = "uk", language = "uk", timezone = "Europe/Kyiv"))))

    private fun AgentFixture.pl(change: (SocialAccount) -> SocialAccount = { it }) =
        memory.put(change(publishable(testAccount(id = "pl", codePrefix = "pl", language = "pl", timezone = "Europe/Warsaw"))))

    private fun AgentFixture.batchPosts(accountId: String) =
        memory.allPosts.values.filter { it.accountId == accountId && it.source == PostSource.Drafter }.sortedBy { it.scheduledAt }

    private fun buttons(call: FakeTelegramApi.Call.Sent): List<String> =
        (call.markup as? InlineKeyboardMarkup)?.inlineKeyboard.orEmpty().flatten().map { it.text }

    private fun AgentFixture.previewCalls() = telegram.callsOf<FakeTelegramApi.Call.Sent>().filter { it.markup != null }

    @Test
    fun aFullWeekForOneAccountIsDraftedPreviewedAndMarkedDone() = runTest {
        val fx = fixture().also { it.ua() }

        val result = fx.graph.batch.run()

        assertIs<BatchResult.Done>(result)
        assertEquals("2026-W43", result.week)
        val posts = fx.batchPosts("ua")
        assertEquals(WEEK_SLOTS.map(Instant::parse), posts.map { it.scheduledAt })
        assertEquals((1..12).map { "ukt%04d".format(it) }, posts.map { it.code })
        posts.forEach {
            assertEquals(it.code, it.id)
            assertEquals(PostStatus.Pending, it.status)
            assertEquals("2026-W43", it.batchWeek)
            assertFalse(it.autoApproved)
            assertFalse(it.needsAttention)
            assertNotNull(it.telegramMessageId)
        }
        assertEquals(12, fx.account("ua").postSeq)

        val batch = fx.memory.allBatches.getValue("2026-W43")
        assertNull(batch.runId)
        assertNull(batch.leaseUntil)
        assertNotNull(batch.finishedAt)
        assertEquals(BatchAccount(done = true, slotsWritten = 12, failedSlots = emptyList()), batch.accounts.getValue("ua"))
        assertEquals(12, fx.memory.allMarkers.size)
        assertEquals("ukt0001", fx.memory.allMarkers["2026-W43/ua_1-1"])
        assertEquals("ukt0012", fx.memory.allMarkers["2026-W43/ua_7-1"])

        val texts = fx.sentTexts()
        assertEquals("@sellsnap.ua, week 2026-W43: 12 drafts", texts.first())
        assertEquals(14, texts.size)
        assertTrue(texts[1].startsWith("@sellsnap.ua | ukt0001 | Mon 19 Oct 08:30"))
        assertTrue(texts[12].startsWith("@sellsnap.ua | ukt0012 | Sun 25 Oct 08:30"))
        assertEquals("Failed slots: none; AI calls today: 24.", texts.last())
        assertEquals(listOf("Approve", "Edit", "Skip"), buttons(fx.telegram.callsOf<FakeTelegramApi.Call.Sent>()[1]).take(3))
    }

    @Test
    fun theWeekIsPlannedAroundPostsThatAlreadyHoldASlotAndExpiresOldPendingOnes() = runTest {
        val fx = fixture().also { it.ua() }
        val sundayEvening = Instant.parse("2026-10-18T16:30:00Z")
        fx.put(testPost("ukt0901", "ua", PostStatus.Pending, sundayEvening).copy(telegramMessageId = 777))
        fx.put(testPost("ukt0902", "ua", PostStatus.Pending, Instant.parse("2026-10-17T05:30:00Z")).copy(source = PostSource.Owner, telegramMessageId = 778))
        fx.put(testPost("ukt0903", "ua", PostStatus.Approved, Instant.parse("2026-10-17T16:30:00Z")))
        fx.put(testPost("ukt0904", "ua", PostStatus.Pending, Instant.parse("2026-10-16T05:30:00Z")).copy(source = PostSource.Drafter, batchWeek = "2026-W42"))
        val wednesday = Instant.parse("2026-10-21T05:30:00Z")
        fx.put(testPost("ukt0905", "ua", PostStatus.Pending, wednesday).copy(source = PostSource.Owner))

        fx.graph.batch.run()

        for (id in listOf("ukt0901", "ukt0904")) {
            assertEquals(PostStatus.Skipped, fx.status(id), id)
            assertEquals("expired", fx.post(id).skipReason, id)
        }
        assertEquals(PostStatus.Pending, fx.status("ukt0902"), "an owner post never expires")
        assertEquals(PostStatus.Approved, fx.status("ukt0903"))
        assertEquals(PostStatus.Pending, fx.status("ukt0905"))

        val refresh = fx.telegram.callsOf<FakeTelegramApi.Call.Edited>().single { it.messageId == 777L }
        assertTrue("| expired" in refresh.text.orEmpty())
        assertNull(refresh.markup, "an expired preview loses its buttons")

        val drafted = fx.batchPosts("ua").filter { it.batchWeek == "2026-W43" }
        assertEquals(11, drafted.size)
        assertTrue(drafted.none { it.scheduledAt == wednesday }, "the occupied slot is not drafted again")
    }

    @Test
    fun aModelThatIsBusyOnCallFiveStopsTheAccountAndARerunCompletesItWithoutDuplicates() = runTest {
        val fx = fixture().also { it.ua() }
        llm.failOnCall += 5

        val first = fx.graph.batch.run()

        assertIs<BatchResult.Deferred>(first)
        assertEquals(AccountStatus.Deferred, first.accounts.single().status)
        assertEquals(2, fx.batchPosts("ua").size)
        val partial = fx.memory.allBatches.getValue("2026-W43")
        assertNull(partial.runId, "the lease is released")
        assertNull(partial.finishedAt)
        assertEquals(BatchAccount(done = false, slotsWritten = 2), partial.accounts.getValue("ua"))
        assertEquals(setOf("2026-W43/ua_1-1", "2026-W43/ua_1-2"), fx.memory.allMarkers.keys)
        val afterFirst = fx.sentTexts()
        assertTrue(afterFirst.none { it.startsWith("@sellsnap.ua, week") }, "no previews while drafting is unfinished")
        assertTrue(afterFirst.single().startsWith("Drafting for ua, week 2026-W43, stopped: The model is busy"))

        val second = fx.graph.batch.run()

        assertIs<BatchResult.Done>(second)
        val posts = fx.batchPosts("ua")
        assertEquals(12, posts.size)
        assertEquals((1..12).map { "ukt%04d".format(it) }, posts.map { it.code })
        assertEquals(WEEK_SLOTS.map(Instant::parse), posts.map { it.scheduledAt })
        assertEquals(12, posts.map { it.code }.toSet().size)
        val previews = fx.previewCalls()
        assertEquals(12, previews.size)
        assertEquals(1, fx.sentTexts().count { it.startsWith("@sellsnap.ua, week 2026-W43: 12 drafts") })
        assertEquals(13, llm.draftRequests.size, "slots 1 and 2 were not drafted again")
        assertTrue(fx.memory.allBatches.getValue("2026-W43").accounts.getValue("ua").done)
    }

    @Test
    fun aRerunAfterTheBatchIsDoneChangesNothing() = runTest {
        val fx = fixture().also { it.ua() }
        fx.graph.batch.run()
        val calls = llm.calls
        val sent = fx.telegram.calls.size

        val again = fx.graph.batch.run()

        assertIs<BatchResult.AlreadyDone>(again)
        assertEquals(calls, llm.calls)
        assertEquals(sent, fx.telegram.calls.size)
        assertEquals(12, fx.batchPosts("ua").size)
    }

    @Test
    fun aHeldLeaseAnswersRunningAndTouchesNothing() = runTest {
        val fx = fixture().also { it.ua() }
        val held = SocialBatch("2026-W43", runId = "other", leaseUntil = SUNDAY.plusSeconds(120), startedAt = SUNDAY, accounts = mapOf("ua" to BatchAccount()))
        fx.memory.put(held)

        val result = fx.graph.batch.run()

        assertEquals(BatchResult.Running("2026-W43"), result)
        assertEquals(0, llm.calls)
        assertTrue(fx.memory.allPosts.isEmpty())
        assertEquals(held, fx.memory.allBatches.getValue("2026-W43"))
    }

    @Test
    fun anExpiredLeaseIsTakenOver() = runTest {
        val fx = fixture().also { it.ua() }
        val started = SUNDAY.minusSeconds(3_600)
        fx.memory.put(
            SocialBatch("2026-W43", runId = "crashed", leaseUntil = SUNDAY.minusSeconds(60), startedAt = started, accounts = mapOf("ua" to BatchAccount(slotsWritten = 0))),
        )

        val result = fx.graph.batch.run()

        assertIs<BatchResult.Done>(result)
        val batch = fx.memory.allBatches.getValue("2026-W43")
        assertNull(batch.runId)
        assertEquals(started, batch.startedAt)
        assertEquals(12, fx.batchPosts("ua").size)
    }

    @Test
    fun aRunThatLosesItsLeaseStopsWritingAndLeavesTheNewOwnersLeaseAlone() = runTest {
        val fx = fixture().also { it.ua() }
        // While the third model call runs, another run takes the lease.
        llm.failWhen = { _ ->
            if (llm.calls == 3) {
                val batch = fx.memory.allBatches.getValue("2026-W43")
                fx.memory.put(batch.copy(runId = "intruder", leaseUntil = SUNDAY.plusSeconds(600)))
            }
            false
        }

        val result = fx.graph.batch.run()

        assertEquals(BatchResult.Running("2026-W43"), result)
        assertEquals(1, fx.batchPosts("ua").size)
        val batch = fx.memory.allBatches.getValue("2026-W43")
        assertEquals("intruder", batch.runId)
        assertEquals(SUNDAY.plusSeconds(600), batch.leaseUntil)
    }

    @Test
    fun aRunThatHitsTheTimeLimitIsTimedOutAndReleasesItsLease() = runTest {
        val fx = fixture().also { it.ua() }
        val hanging = object : LlmClient {
            override suspend fun complete(request: LlmRequest): LlmResult {
                delay(1.hours)
                error("unreachable")
            }
        }
        val usage = UsageTracker(fx.store, fx.clock, 200)
        val brand = BrandContext.load()
        val pipeline = DraftPipeline(Drafter(hanging, usage, brand, fx.config.draftModel), DraftChecker(hanging, usage, brand, fx.config.checkModel))
        val previews = Previews(fx.images, fx.config.postLinkTemplate)
        val runner = BatchRunner(
            store = fx.store,
            pipeline = pipeline,
            releases = FakeReleaseNotesSource(),
            previews = previews,
            sender = PreviewSender(fx.telegram, previews, fx.store, AgentFixture.OWNER),
            telegram = fx.telegram,
            alerts = Alerts(fx.store, fx.telegram, fx.clock, AgentFixture.OWNER),
            usage = usage,
            clock = fx.clock,
            ownerChatId = AgentFixture.OWNER,
            timeout = 1.minutes,
        )

        val result = runner.run()

        assertEquals(BatchResult.TimedOut("2026-W43"), result)
        val batch = fx.memory.allBatches.getValue("2026-W43")
        assertNull(batch.runId)
        assertFalse(batch.accounts.getValue("ua").done)
    }

    @Test
    fun doneIsPerAccountSoARerunOnlyRetriesTheAccountThatStopped() = runTest {
        val fx = fixture().also { it.ua(); it.pl() }
        llm.failWhen = { "LANGUAGE: Polish" in it.user }

        val first = fx.graph.batch.run()

        assertIs<BatchResult.Deferred>(first)
        assertEquals(setOf("ua", "pl"), first.accounts.map { it.accountId }.toSet())
        val batch = fx.memory.allBatches.getValue("2026-W43")
        assertTrue(batch.accounts.getValue("ua").done)
        assertFalse(batch.accounts.getValue("pl").done)
        assertEquals(12, fx.batchPosts("ua").size)
        assertEquals(0, fx.batchPosts("pl").size)
        val ukrainianRequests = llm.draftRequests.count { "LANGUAGE: Ukrainian" in it.user }

        llm.failWhen = { false }
        val second = fx.graph.batch.run()

        assertIs<BatchResult.Done>(second)
        assertEquals(listOf("pl"), second.accounts.map { it.accountId })
        assertEquals(ukrainianRequests, llm.draftRequests.count { "LANGUAGE: Ukrainian" in it.user })
        assertEquals(12, fx.batchPosts("ua").size)
        assertEquals((1..12).map { "plt%04d".format(it) }, fx.batchPosts("pl").map { it.code })
        // Warsaw leaves summer time at the same instant as Kyiv, so Sunday 08:30 is 07:30 UTC.
        assertEquals(Instant.parse("2026-10-25T07:30:00Z"), fx.batchPosts("pl").last().scheduledAt)
        assertTrue(fx.memory.allBatches.getValue("2026-W43").accounts.values.all { it.done })
    }

    @Test
    fun aManualRunForOneAccountLeavesTheOthersAlone() = runTest {
        val fx = fixture().also { it.ua(); it.pl() }

        val manual = fx.graph.batch.run(listOf("ua"))

        assertIs<BatchResult.Done>(manual)
        assertEquals(listOf("ua"), manual.accounts.map { it.accountId })
        assertEquals(12, fx.batchPosts("ua").size)
        assertEquals(0, fx.batchPosts("pl").size)
        assertEquals(setOf("ua"), fx.memory.allBatches.getValue("2026-W43").accounts.keys)
        assertNotNull(fx.memory.allBatches.getValue("2026-W43").finishedAt)

        val weekly = fx.graph.batch.run()

        assertIs<BatchResult.Done>(weekly)
        assertEquals(listOf("pl"), weekly.accounts.map { it.accountId })
        assertEquals(12, fx.batchPosts("pl").size)
    }

    @Test
    fun theWeeklyRunSkipsAccountsWithoutATokenPausedOnesAndOnesWithBatchesOff() = runTest {
        val fx = fixture()
        fx.ua { it.copy(batchEnabled = false) }
        fx.pl { it.copy(paused = true) }
        fx.memory.put(testAccount(id = "other", codePrefix = "ot", language = "uk"))

        val result = fx.graph.batch.run()

        assertEquals(BatchResult.Done("2026-W43", emptyList()), result)
        assertEquals(0, llm.calls)

        val named = fx.graph.batch.run(listOf("ua"))
        assertIs<BatchResult.Done>(named)
        assertEquals(12, fx.batchPosts("ua").size)
    }

    @Test
    fun autoModeApprovesThePostsExceptTheOnesThatNeedAttention() = runTest {
        val fx = fixture().also { it.ua { account -> account.copy(mode = AccountMode.Auto) } }
        llm.flaggedSlots += setOf(3, 7)

        fx.graph.batch.run()

        val posts = fx.batchPosts("ua")
        assertEquals(12, posts.size)
        val flagged = posts.filter { it.needsAttention }
        assertEquals(listOf("ukt0003", "ukt0007"), flagged.map { it.code })
        flagged.forEach {
            assertEquals(PostStatus.Pending, it.status)
            assertFalse(it.autoApproved)
            assertNotNull(it.checkNotes)
        }
        val approved = posts.filterNot { it.needsAttention }
        assertEquals(10, approved.size)
        approved.forEach {
            assertEquals(PostStatus.Approved, it.status, it.code)
            assertTrue(it.autoApproved, it.code)
        }
        val previews = fx.previewCalls()
        assertEquals(12, previews.size)
        assertEquals(10, previews.count { buttons(it) == listOf("Delete before slot") })
        assertEquals(2, previews.count { buttons(it).first() == "Approve" })
    }

    @Test
    fun aSlotWhoseDraftFailsIsRecordedWithoutAMarkerAndARerunRetriesIt() = runTest {
        val fx = fixture().also { it.ua() }
        llm.tooLongSlots += 4

        val first = fx.graph.batch.run()

        assertIs<BatchResult.Done>(first)
        assertEquals(listOf("2-2"), first.accounts.single().failedSlots)
        assertEquals(11, fx.batchPosts("ua").size)
        assertFalse("2026-W43/ua_2-2" in fx.memory.allMarkers)
        val entry = fx.memory.allBatches.getValue("2026-W43").accounts.getValue("ua")
        assertTrue(entry.done)
        assertEquals(listOf("2-2"), entry.failedSlots)
        assertEquals("Failed slots: Tue slot 2; AI calls today: 24.", fx.sentTexts().last())

        llm.tooLongSlots.clear()
        val rerun = fx.graph.batch.run()

        assertIs<BatchResult.Done>(rerun)
        assertEquals(12, fx.batchPosts("ua").size)
        assertEquals(emptyList(), fx.memory.allBatches.getValue("2026-W43").accounts.getValue("ua").failedSlots)
        assertEquals(12, fx.batchPosts("ua").map { it.code }.toSet().size)
        assertEquals(1, fx.previewCalls().count { "ukt0012" in it.text.orEmpty() })
        assertEquals(12, fx.previewCalls().size)
        assertIs<BatchResult.AlreadyDone>(fx.graph.batch.run())
    }

    @Test
    fun aTipMayNameTheAppOnEverySecondTipInTimeOrderAndAnUpdateAlwaysDoes() = runTest {
        val release = ReleaseNotes(
            version = "3.3",
            date = "2026-10-12",
            changes = listOf(ReleaseChange("drafts", mapOf("en" to "Drafts"), mapOf("en" to "Unfinished listings are kept."))),
        )
        val fx = fixture(listOf(release)).also { it.ua() }

        fx.graph.batch.run()

        val requests = llm.draftRequests
        val tips = requests.filter { "PILLAR: selling tip" in it.user }
        assertEquals(11, tips.size)
        tips.forEachIndexed { index, request ->
            assertEquals(index % 2 == 1, "APP MENTION: allowed" in request.user, "tip ${index + 1}")
            assertEquals(index % 2 != 1, "APP MENTION: not allowed" in request.user, "tip ${index + 1}")
        }
        val update = requests.single { "PILLAR: product update" in it.user }
        assertTrue("APP MENTION: required" in update.user)
        assertTrue("LINK REPLY: required" in update.user)

        val posts = fx.batchPosts("ua")
        val sunday = posts.last()
        assertEquals(Pillar.Update, sunday.pillar)
        assertEquals("release:3.3", sunday.sourceRef)
        assertEquals("Спробуйте SellSnap: {link}", sunday.replyText)
        assertEquals("ukt0011", posts[10].code)
        assertEquals("uku0012", sunday.code)
        val tipPosts = posts.filter { it.pillar == Pillar.Tip }
        tipPosts.forEachIndexed { index, post -> assertEquals(index % 2 == 1, "SellSnap" in post.text, post.code) }
    }

    @Test
    fun aReleaseThatAnUpdatePostAlreadyAnnouncedIsNotDraftedAgain() = runTest {
        val release = ReleaseNotes("3.3", "2026-10-12", listOf(ReleaseChange("drafts", mapOf("en" to "Drafts"), mapOf("en" to "Kept."))))
        val old = ReleaseNotes("3.0", "2026-08-01", listOf(ReleaseChange("x", mapOf("en" to "X"), mapOf("en" to "Y"))))
        val fx = fixture(listOf(old, release)).also { it.ua() }
        fx.put(testPost("ukt0901", "ua", PostStatus.Published, Instant.parse("2026-10-14T05:30:00Z")).copy(pillar = Pillar.Update, sourceRef = "release:3.3"))

        fx.graph.batch.run()

        assertTrue(llm.draftRequests.none { "PILLAR: product update" in it.user })
        assertEquals(12, fx.batchPosts("ua").count { it.pillar == Pillar.Tip })
    }

    private companion object {
        /** Sunday 18 October 2026, 09:00 in Kyiv. */
        val SUNDAY: Instant = Instant.parse("2026-10-18T06:00:00Z")

        /** The launch week of the Kyiv account: Monday to Friday twice, Saturday and Sunday once. Kyiv leaves summer time at 01:00 UTC on 25 October. */
        val WEEK_SLOTS = listOf(
            "2026-10-19T05:30:00Z", "2026-10-19T16:30:00Z",
            "2026-10-20T05:30:00Z", "2026-10-20T16:30:00Z",
            "2026-10-21T05:30:00Z", "2026-10-21T16:30:00Z",
            "2026-10-22T05:30:00Z", "2026-10-22T16:30:00Z",
            "2026-10-23T05:30:00Z", "2026-10-23T16:30:00Z",
            "2026-10-24T05:30:00Z",
            "2026-10-25T06:30:00Z",
        )
    }
}
