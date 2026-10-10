package com.sirelon.sellsnap.draft

import com.sirelon.sellsnap.brand.BrandContext
import com.sirelon.sellsnap.brand.ReleaseChange
import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.fakes.FakeLlmClient
import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.llm.LlmFailure
import com.sirelon.sellsnap.llm.LlmResult
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.UsageCounters
import com.sirelon.sellsnap.spend.UsageTracker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DraftPipelineTest {
    private val store = InMemorySocialStore()
    private val clock = MutableClock(Instant.parse("2026-10-18T07:00:00Z"))
    private val usage = UsageTracker(store, clock, dailyCallLimit = 200)
    private val llm = FakeLlmClient()
    private val brand = BrandContext.load()
    private val pipeline = DraftPipeline(
        Drafter(llm, usage, brand, DRAFT),
        DraftChecker(llm, usage, brand, CHECK),
    )

    private val release = ReleaseNotes(
        version = "3.3",
        date = "2026-10-12",
        changes = listOf(
            ReleaseChange(
                id = "drafts",
                title = mapOf("en" to "Drafts", "uk" to "Чернетки", "pl" to "Szkice"),
                summary = mapOf("en" to "Unfinished listings are kept.", "uk" to "Незавершені оголошення зберігаються.", "pl" to "Niedokończone ogłoszenia są zapisywane."),
            ),
        ),
    )

    private val ukTip = SlotBrief(Pillar.Tip, "uk", listOf("Старий допис про світло"))
    private val plTip = SlotBrief(Pillar.Tip, "pl", emptyList())
    private val ukUpdate = SlotBrief(Pillar.Update, "uk", emptyList(), release, linkReplyAllowed = true)

    private val goodUk = "Сфотографуйте річ біля вікна: денне світло показує справжній колір 📸"
    private val goodPl = "Sfotografuj rzecz przy oknie: dzienne światło pokazuje prawdziwy kolor 📸"

    private fun draftReply(text: String, replyText: String? = null, ids: List<String> = emptyList()) = FakeLlmClient.reply(
        DRAFT,
        buildJsonObject {
            put("text", text)
            if (replyText != null) put("replyText", replyText)
            put("claimedFeatureIds", JsonArray(ids.map(::JsonPrimitive)))
        }.toString(),
    )

    private fun checkReply(
        language: String,
        unsupported: List<String> = emptyList(),
        findings: List<String> = emptyList(),
        backTranslation: String? = null,
        naturalnessOk: Boolean? = null,
        issues: List<String> = emptyList(),
    ) = FakeLlmClient.reply(
        CHECK,
        buildJsonObject {
            put("detectedLanguage", language)
            put("unsupportedClaims", JsonArray(unsupported.map(::JsonPrimitive)))
            put("guardrailFindings", JsonArray(findings.map(::JsonPrimitive)))
            put("backTranslationEn", backTranslation?.let(::JsonPrimitive) ?: JsonNull)
            put("naturalnessOk", naturalnessOk?.let(::JsonPrimitive) ?: JsonNull)
            put("naturalnessIssues", JsonArray(issues.map(::JsonPrimitive)))
        }.toString(),
    )

    private suspend fun run(brief: SlotBrief, publishedCount: Int = 12) = pipeline.run(brief, publishedCount, LINK_URL)

    private fun script(vararg replies: LlmResult) = replies.forEach(llm::enqueue)

    @Test
    fun aCleanUkrainianDraftPassesWithOneDraftCallAndOneCheckCall() = runTest {
        script(draftReply(goodUk, ids = listOf("f1")), checkReply("uk"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertEquals(goodUk, outcome.text)
        assertNull(outcome.replyText)
        assertNull(outcome.backTranslation)
        assertFalse(outcome.needsAttention)
        assertNull(outcome.checkNotes)
        assertEquals(listOf(DRAFT, CHECK), llm.requests.map { it.model })
        llm.requests.forEach {
            assertEquals("low", it.thinkingLevel)
            assertTrue(it.responseSchema != null)
        }
        assertTrue("Старий допис про світло" in llm.requests[0].user)
        assertTrue(goodUk in llm.requests[1].user)
        assertTrue("f1" in llm.requests[1].user)
    }

    @Test
    fun everyCallIsRecordedInTheUsageTracker() = runTest {
        script(draftReply(goodUk), checkReply("uk"))

        run(ukTip)

        assertEquals(1L, usage.callsToday(DRAFT))
        assertEquals(1L, usage.callsToday(CHECK))
        assertEquals(UsageCounters(1, 100, 50, 20), store.allUsage.getValue("20261018").getValue(DRAFT))
    }

    @Test
    fun theAppMentionCapReachesTheDrafterAndStaysThroughARegeneration() = runTest {
        val noMention = SlotBrief(Pillar.Tip, "uk", emptyList(), mayMentionApp = false)
        script(draftReply("а".repeat(500)), draftReply(goodUk), checkReply("uk"))

        assertIs<DraftOutcome.Draft>(run(noMention))

        assertEquals(listOf(DRAFT, DRAFT, CHECK), llm.requests.map { it.model })
        llm.requests.filter { it.model == DRAFT }.forEach {
            assertTrue("APP MENTION: not allowed" in it.user)
            assertFalse("APP MENTION: allowed" in it.user)
        }

        script(draftReply(goodUk), checkReply("uk"))
        assertIs<DraftOutcome.Draft>(run(ukTip))
        assertTrue("APP MENTION: allowed" in llm.requests[3].user)
    }

    @Test
    fun aRuleFailureRegeneratesOnceWithTheViolationsAndThenPasses() = runTest {
        val tooLong = "а".repeat(500)
        script(draftReply(tooLong), draftReply(goodUk), checkReply("uk"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertEquals(goodUk, outcome.text)
        assertFalse(outcome.needsAttention)
        assertEquals(listOf(DRAFT, DRAFT, CHECK), llm.requests.map { it.model })
        val retry = llm.requests[1].user
        assertTrue("YOUR PREVIOUS DRAFT WAS REJECTED" in retry)
        assertTrue("Shorten text" in retry)
        assertTrue(tooLong in retry)
    }

    @Test
    fun twoRuleFailuresEndInDraftFailedWithNoCheckCall() = runTest {
        script(draftReply("Read more at https://example.test now"), draftReply("Still bad: www.example.test"))

        val outcome = assertIs<DraftOutcome.DraftFailed>(run(ukTip))

        assertTrue("raw URL" in outcome.reason)
        assertEquals(listOf(DRAFT, DRAFT), llm.requests.map { it.model })
    }

    @Test
    fun aLinkInTheTextIsRejectedEvenForAnUpdate() = runTest {
        script(
            draftReply("Нове в застосунку: {link}", replyText = "Спробуйте: {link}"),
            draftReply("Нове: чернетки зберігають незавершені оголошення ✍️", replyText = "Спробуйте: {link}", ids = listOf("drafts")),
            checkReply("uk"),
        )

        val outcome = assertIs<DraftOutcome.Draft>(run(ukUpdate))

        assertEquals("Спробуйте: {link}", outcome.replyText)
        assertFalse("{link}" in outcome.text)
        assertTrue("A link may only appear in replyText" in llm.requests[1].user)
        assertTrue("RELEASE 3.3" in llm.requests[0].user)
        assertTrue("RELEASE 3.3" in llm.requests[2].user)
    }

    @Test
    fun anUpdateWithoutALinkReplyIsRejected() = runTest {
        script(draftReply("Нове: чернетки"), draftReply("Нове: чернетки", replyText = "Спробуйте: {link}"), checkReply("uk"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukUpdate))

        assertEquals("Спробуйте: {link}", outcome.replyText)
        assertTrue("Add replyText" in llm.requests[1].user)
    }

    @Test
    fun aTipWithAReplyIsRejected() = runTest {
        script(draftReply(goodUk, replyText = "Ще: {link}"), draftReply(goodUk), checkReply("uk"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertNull(outcome.replyText)
        assertTrue("Leave replyText out" in llm.requests[1].user)
    }

    @Test
    fun theUkrainianLatinAiSpellingIsRejected() = runTest {
        script(draftReply("AI напише оголошення за вас"), draftReply("ШІ напише оголошення за вас ✨"), checkReply("uk"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.text.startsWith("ШІ"))
        assertTrue("ШІ" in llm.requests[1].user)
    }

    @Test
    fun aCheckerFindingRegeneratesOnceAndTheSecondCleanDraftPasses() = runTest {
        script(
            draftReply(goodUk),
            checkReply("uk", unsupported = listOf("works offline")),
            draftReply("Сфотографуйте річ біля вікна ✨"),
            checkReply("uk"),
        )

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertEquals("Сфотографуйте річ біля вікна ✨", outcome.text)
        assertFalse(outcome.needsAttention)
        assertEquals(listOf(DRAFT, CHECK, DRAFT, CHECK), llm.requests.map { it.model })
        assertTrue("Unsupported product claim: works offline" in llm.requests[2].user)
    }

    @Test
    fun twoCheckerFailuresGiveADraftThatNeedsAttention() = runTest {
        script(
            draftReply(goodUk),
            checkReply("uk", unsupported = listOf("works offline")),
            draftReply("Сфотографуйте річ біля вікна 🌤"),
            checkReply("uk", findings = listOf("2. Implies that OLX endorses the app.")),
        )

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.needsAttention)
        assertEquals("Сфотографуйте річ біля вікна 🌤", outcome.text)
        assertTrue("OLX endorses" in outcome.checkNotes.orEmpty())
        assertEquals(4, llm.requests.size)
    }

    @Test
    fun aWrongDetectedLanguageFailsTheCheck() = runTest {
        script(draftReply(goodUk), checkReply("pl"), draftReply(goodUk), checkReply("uk"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertFalse(outcome.needsAttention)
        assertTrue("written in \"pl\", not in uk" in llm.requests[2].user)
    }

    @Test
    fun aRegeneratedDraftThatBreaksTheRulesLeavesTheFirstDraftFlagged() = runTest {
        script(draftReply(goodUk), checkReply("uk", unsupported = listOf("works offline")), draftReply("See https://example.test"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertEquals(goodUk, outcome.text)
        assertTrue(outcome.needsAttention)
        assertTrue("works offline" in outcome.checkNotes.orEmpty())
        assertTrue("regenerated draft broke the rules" in outcome.checkNotes.orEmpty())
        assertEquals(3, llm.requests.size)
    }

    @Test
    fun aCheckerThatThrowsFailsClosedWithoutRegenerating() = runTest {
        script(draftReply(goodUk))
        llm.enqueueFailure(IllegalStateException("boom with a secret"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.needsAttention)
        assertEquals(goodUk, outcome.text)
        val notes = outcome.checkNotes.orEmpty()
        assertTrue("The check did not run" in notes && "IllegalStateException" in notes, notes)
        assertFalse("secret" in notes)
        assertEquals(2, llm.requests.size)
    }

    @Test
    fun aCheckerFailureOfTheClientFailsClosed() = runTest {
        script(draftReply(goodUk))
        llm.enqueueFailure(LlmFailure.Transient("Gemini temporary error (HTTP 503)"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.needsAttention)
        assertTrue("Gemini temporary error (HTTP 503)" in outcome.checkNotes.orEmpty())
    }

    @Test
    fun aCheckerRefusalFailsClosed() = runTest {
        script(draftReply(goodUk))
        llm.enqueueFailure(LlmFailure.Refused("Gemini blocked the answer (SAFETY)"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.needsAttention)
        assertTrue("blocked the answer" in outcome.checkNotes.orEmpty())
    }

    @Test
    fun anUnreadableCheckerAnswerFailsClosed() = runTest {
        script(draftReply(goodUk), FakeLlmClient.reply(CHECK, "not json"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.needsAttention)
        assertTrue("not valid JSON" in outcome.checkNotes.orEmpty())
    }

    @Test
    fun aCheckerCutOffByTheTokenLimitFailsClosed() = runTest {
        script(draftReply(goodUk), FakeLlmClient.reply(CHECK, "{\"detectedLanguage\":\"uk\"", LlmResult.FINISH_MAX_TOKENS))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.needsAttention)
        assertTrue("cut off" in outcome.checkNotes.orEmpty())
    }

    @Test
    fun theCheckerAtItsUsageLimitFailsClosedWithoutACall() = runTest {
        store.addUsage("20261018", CHECK, UsageCounters(calls = 180))
        script(draftReply(goodUk))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.needsAttention)
        assertTrue("daily call limit" in outcome.checkNotes.orEmpty())
        assertEquals(listOf(DRAFT), llm.requests.map { it.model })
    }

    @Test
    fun theDrafterAtItsUsageLimitStopsWithATypedOutcomeAndMakesNoCall() = runTest {
        store.addUsage("20261018", DRAFT, UsageCounters(calls = 180))

        val outcome = assertIs<DraftOutcome.UsageStop>(run(ukTip))

        assertEquals(DRAFT, outcome.model)
        assertTrue(llm.requests.isEmpty())
    }

    @Test
    fun aUsageLimitReachedBetweenTheDraftAndItsRegenerationKeepsTheFlaggedDraft() = runTest {
        script(draftReply(goodUk), checkReply("uk", unsupported = listOf("works offline")))
        // The first draft call takes the drafter to 180 calls, its limit; the regeneration is then refused.
        store.addUsage("20261018", DRAFT, UsageCounters(calls = 179))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertTrue(outcome.needsAttention)
        assertEquals(goodUk, outcome.text)
        assertTrue("Regeneration skipped" in outcome.checkNotes.orEmpty())
        assertEquals(listOf(DRAFT, CHECK), llm.requests.map { it.model })
    }

    @Test
    fun anUnreachableDrafterDefersTheSlot() = runTest {
        llm.enqueueFailure(LlmFailure.Transient("Gemini temporary error (HTTP 503)"))

        val outcome = assertIs<DraftOutcome.Deferred>(run(ukTip))

        assertEquals("Gemini temporary error (HTTP 503)", outcome.reason)
        assertEquals(1, llm.requests.size)
    }

    @Test
    fun aRateLimitedDrafterDefersTheSlot() = runTest {
        llm.enqueueFailure(LlmFailure.RateLimited("Gemini rate limit reached (HTTP 429)"))

        assertIs<DraftOutcome.Deferred>(run(ukTip))
    }

    @Test
    fun aRefusedDraftIsRetriedOnceAndThenFails() = runTest {
        llm.enqueueFailure(LlmFailure.Refused("Gemini blocked the answer (SAFETY)"))
        llm.enqueueFailure(LlmFailure.Refused("Gemini blocked the answer (SAFETY)"))

        val outcome = assertIs<DraftOutcome.DraftFailed>(run(ukTip))

        assertTrue("blocked the answer" in outcome.reason)
        assertEquals(2, llm.requests.size)
    }

    @Test
    fun aCutOffDraftIsRetriedAndTheRetryCanPass() = runTest {
        script(FakeLlmClient.reply(DRAFT, "{\"text\":\"abc", LlmResult.FINISH_MAX_TOKENS), draftReply(goodUk), checkReply("uk"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertEquals(goodUk, outcome.text)
        assertEquals(3, llm.requests.size)
    }

    @Test
    fun aPolishDraftCarriesTheBackTranslation() = runTest {
        script(
            draftReply(goodPl),
            checkReply("pl", backTranslation = "Photograph the item by a window: daylight shows the true colour.", naturalnessOk = true),
        )

        val outcome = assertIs<DraftOutcome.Draft>(run(plTip))

        assertEquals(goodPl, outcome.text)
        assertEquals("Photograph the item by a window: daylight shows the true colour.", outcome.backTranslation)
        assertFalse(outcome.needsAttention)
        assertTrue("Polish" in llm.requests[1].system && "backTranslationEn" in llm.requests[1].system)
    }

    @Test
    fun aPolishDraftThatIsNotNaturalRegeneratesAndKeepsTheNewBackTranslation() = runTest {
        script(
            draftReply(goodPl),
            checkReply("pl", backTranslation = "first", naturalnessOk = false, issues = listOf("stiff opening")),
            draftReply("Zrób zdjęcie przy oknie, bo światło dzienne nie kłamie ✨"),
            checkReply("pl", backTranslation = "second", naturalnessOk = true),
        )

        val outcome = assertIs<DraftOutcome.Draft>(run(plTip))

        assertEquals("second", outcome.backTranslation)
        assertFalse(outcome.needsAttention)
        assertTrue("stiff opening" in llm.requests[2].user)
    }

    @Test
    fun aPolishDraftWithoutAVerdictOrBackTranslationNeedsAttention() = runTest {
        script(draftReply(goodPl), checkReply("pl"), draftReply(goodPl), checkReply("pl"))

        val outcome = assertIs<DraftOutcome.Draft>(run(plTip))

        assertTrue(outcome.needsAttention)
        assertTrue("natural" in outcome.checkNotes.orEmpty())
        assertTrue("back-translation" in outcome.checkNotes.orEmpty())
    }

    @Test
    fun aUkrainianDraftNeedsNeitherBackTranslationNorNaturalness() = runTest {
        script(draftReply(goodUk), checkReply("uk"))

        val outcome = assertIs<DraftOutcome.Draft>(run(ukTip))

        assertNull(outcome.backTranslation)
        assertFalse(outcome.needsAttention)
    }

    @Test
    fun aPolishDraftWithACyrillicLetterIsRejectedAndRegenerated() = runTest {
        script(
            draftReply("Zrób zdjęcie przy oknie, привіт ✨"),
            draftReply(goodPl),
            checkReply("pl", backTranslation = "ok", naturalnessOk = true),
        )

        val outcome = assertIs<DraftOutcome.Draft>(run(plTip))

        assertEquals(goodPl, outcome.text)
        assertTrue("Cyrillic" in llm.requests[1].user)
    }

    @Test
    fun twoPolishDraftsWithCyrillicEndInDraftFailed() = runTest {
        script(draftReply("Cześć, привіт"), draftReply("Hej, світ"))

        val outcome = assertIs<DraftOutcome.DraftFailed>(run(plTip))

        assertTrue("Cyrillic" in outcome.reason)
        assertEquals(2, llm.requests.size)
    }

    @Test
    fun aCyrillicLetterInAPolishReplyIsRejectedToo() = runTest {
        val plUpdate = SlotBrief(Pillar.Update, "pl", emptyList(), release, linkReplyAllowed = true)
        script(draftReply("Nowość: szkice", replyText = "Спробуй: {link}"), draftReply("Nowość: szkice", replyText = "Wypróbuj: {link}"), checkReply("pl", backTranslation = "x", naturalnessOk = true))

        val outcome = assertIs<DraftOutcome.Draft>(run(plUpdate))

        assertEquals("Wypróbuj: {link}", outcome.replyText)
        assertTrue("Cyrillic" in llm.requests[1].user)
    }

    @Test
    fun theLinkReplyOfAnUpdateGetsTheSameLengthRuleAsTheText() = runTest {
        val longReply = "Спробуйте: {link} " + "я".repeat(480)
        script(
            draftReply("Нове: чернетки", replyText = longReply),
            draftReply("Нове: чернетки", replyText = "Спробуйте: {link}"),
            checkReply("uk"),
        )

        val outcome = assertIs<DraftOutcome.Draft>(run(ukUpdate))

        assertEquals("Спробуйте: {link}", outcome.replyText)
        assertTrue("Shorten replyText" in llm.requests[1].user)
    }

    private companion object {
        const val DRAFT = "draft-model"
        const val CHECK = "check-model"
        const val LINK_URL = "https://sirelon.github.io/SellSnap/?lang=uk&p=ua0000"
    }
}
