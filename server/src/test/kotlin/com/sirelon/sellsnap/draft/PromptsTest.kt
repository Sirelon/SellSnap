package com.sirelon.sellsnap.draft

import com.sirelon.sellsnap.brand.BrandContext
import com.sirelon.sellsnap.brand.ReleaseChange
import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.model.Pillar
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptsTest {
    private val brand = BrandContext.load()

    // The letters that only the russian alphabet has, as code points: yery, e, hard sign, yo, both cases.
    private val onlyRussian = Regex("[\\u044B\\u044D\\u044A\\u0451\\u042B\\u042D\\u042A\\u0401]")

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

    private fun tipBrief(language: String = "uk", recent: List<String> = emptyList()) =
        SlotBrief(Pillar.Tip, language, recent)

    private fun updateBrief(language: String = "uk") =
        SlotBrief(Pillar.Update, language, emptyList(), release, linkReplyAllowed = true)

    @Test
    fun theSystemPromptHoldsAFullNumberedChecklist() {
        val prompt = Prompts.drafterSystem(brand, "uk")

        assertTrue("CHECKLIST" in prompt)
        (1..12).forEach { assertTrue(Regex("(?m)^$it\\. ").containsMatchIn(prompt), "point $it") }
        listOf("490", "UTF-8", "range", "http", "{link}", "replyText", "OLX", "independent").forEach {
            assertTrue(it in prompt, it)
        }
    }

    @Test
    fun theSystemPromptHoldsVoiceSamplesInTheAccountLanguageAndEnglish() {
        val uk = Prompts.drafterSystem(brand, "uk")
        val pl = Prompts.drafterSystem(brand, "pl")

        assertTrue("Привіт, ласкаво просимо в SellSnap 👋" in uk)
        assertTrue("Hey, welcome to SellSnap 👋" in uk)
        assertEquals(20, brand.voiceSamplesIn("pl").size)
        brand.voiceSamplesIn("pl").forEach { assertTrue(it.text in pl, it.key) }
        brand.voiceSamplesIn("en").forEach { assertTrue(it.text in pl, it.key) }
        assertFalse(brand.voiceSamplesIn("uk").first().text in pl)
    }

    @Test
    fun theSystemPromptListsEveryFeatureWithItsIdInBothLanguages() {
        val prompt = Prompts.drafterSystem(brand, "pl")

        brand.featuresIn("pl").forEach { assertTrue("${it.id}: ${it.text}" in prompt, it.id) }
        brand.featuresIn("en").forEach { assertTrue("${it.id}: ${it.text}" in prompt, it.id) }
    }

    @Test
    fun languageRulesFollowTheAccountLanguage() {
        val uk = Prompts.drafterSystem(brand, "uk")
        val pl = Prompts.drafterSystem(brand, "pl")

        assertTrue("\"ШІ\"" in uk)
        assertTrue("ефір" in uk)
        assertTrue("standard Ukrainian" in uk)
        assertTrue("write the AI as \"AI\"" in pl)
        assertTrue("No Cyrillic letter appears anywhere" in pl)
        assertTrue("na antenie" in pl)
        assertFalse("\"ШІ\"" in pl)
    }

    @Test
    fun noPromptHoldsALetterThatOnlyRussianUses() {
        val prompts = listOf("uk", "pl", "en").flatMap { language ->
            listOf(
                Prompts.drafterSystem(brand, language),
                Prompts.checkerSystem(brand, language),
                Prompts.drafterUser(tipBrief(language)),
                Prompts.checkerUser(tipBrief(language), "text", null, emptyList()),
            )
        } + Prompts.drafterUser(updateBrief("uk")) + Prompts.drafterUser(updateBrief("pl"))
        prompts.forEach { assertFalse(onlyRussian.containsMatchIn(it)) }
    }

    @Test
    fun theUserPromptHoldsPillarLanguageRecentPostsAndTheLinkRule() {
        val recent = (1..25).map { "recent post number $it" }

        val tip = Prompts.drafterUser(tipBrief("pl", recent))

        assertTrue("PILLAR: selling tip" in tip)
        assertTrue("LANGUAGE: Polish" in tip)
        assertTrue("LINK REPLY: not allowed" in tip)
        assertTrue("recent post number 20" in tip)
        assertFalse("recent post number 21" in tip)
        assertFalse("RELEASE" in tip)
    }

    @Test
    fun anUpdatePromptCarriesTheReleaseNoteInTheAccountLanguage() {
        val prompt = Prompts.drafterUser(updateBrief("uk"))

        assertTrue("PILLAR: product update" in prompt)
        assertTrue("RELEASE 3.3 (2026-10-12)" in prompt)
        assertTrue("Чернетки" in prompt)
        assertTrue("Незавершені оголошення зберігаються." in prompt)
        assertTrue("LINK REPLY: required" in prompt)
        assertTrue("{link}" in prompt)
        assertTrue("none yet" in prompt)
    }

    @Test
    fun aRejectionFeedsThePreviousDraftAndEveryProblemBack() {
        val prompt = Prompts.drafterUser(tipBrief(), Rejection("old text", "old reply", listOf("Shorten text.", "Remove the raw URL.")))

        assertTrue("YOUR PREVIOUS DRAFT WAS REJECTED" in prompt)
        assertTrue("Previous text: old text" in prompt)
        assertTrue("Previous replyText: old reply" in prompt)
        assertTrue("- Shorten text." in prompt)
        assertTrue("- Remove the raw URL." in prompt)
    }

    @Test
    fun theCheckerPromptAsksForABackTranslationOnlyInPolish() {
        val pl = Prompts.checkerSystem(brand, "pl")
        val uk = Prompts.checkerSystem(brand, "uk")

        assertTrue("backTranslationEn" in pl && "naturalnessOk to true" in pl)
        assertTrue("Leave backTranslationEn and naturalnessOk out" in uk)
        assertTrue("detectedLanguage" in uk && "unsupportedClaims" in uk && "CHECKLIST" in uk)
    }

    @Test
    fun theCheckerUserPromptHoldsTheDraftAndTheReleaseNoteOfAnUpdate() {
        val prompt = Prompts.checkerUser(updateBrief("pl"), "Post body", "Reply body {link}", listOf("f1", "drafts"))

        assertTrue("Required language: Polish (pl)" in prompt)
        assertTrue("f1, drafts" in prompt)
        assertTrue("valid source of claims" in prompt)
        assertTrue("Szkice" in prompt)
        assertTrue("POST TEXT:\nPost body" in prompt)
        assertTrue("REPLY TEXT:\nReply body {link}" in prompt)
    }

    @Test
    fun theDrafterSchemaIsAnObjectOfTextReplyTextAndClaimedFeatureIds() {
        val schema = Prompts.drafterSchema

        assertEquals("object", schema.type())
        assertEquals(setOf("text", "replyText", "claimedFeatureIds"), schema.properties().keys)
        assertEquals(listOf("text", "claimedFeatureIds"), schema.required())
        assertEquals("string", schema.properties().getValue("text").jsonObject.type())
        assertEquals("array", schema.properties().getValue("claimedFeatureIds").jsonObject.type())
    }

    @Test
    fun theCheckerSchemaHoldsTheLanguageClaimsFindingsBackTranslationAndNaturalness() {
        val schema = Prompts.checkerSchema

        assertEquals("object", schema.type())
        assertEquals(
            setOf("detectedLanguage", "unsupportedClaims", "guardrailFindings", "backTranslationEn", "naturalnessOk", "naturalnessIssues"),
            schema.properties().keys,
        )
        assertEquals(listOf("detectedLanguage", "unsupportedClaims", "guardrailFindings", "naturalnessIssues"), schema.required())
        assertEquals("boolean", schema.properties().getValue("naturalnessOk").jsonObject.type())
        assertEquals("array", schema.properties().getValue("unsupportedClaims").jsonObject.type())
    }

    @Test
    fun aBriefNeedsADraftablePillarAndMatchingReleaseNote() {
        assertFailsWith<IllegalArgumentException> { SlotBrief(Pillar.Poll, "uk", emptyList()) }
        assertFailsWith<IllegalArgumentException> { SlotBrief(Pillar.Update, "uk", emptyList()) }
        assertFailsWith<IllegalArgumentException> { SlotBrief(Pillar.Tip, "uk", emptyList(), release) }
    }

    private fun JsonObject.type(): String = getValue("type").jsonPrimitive.content

    private fun JsonObject.properties(): JsonObject = getValue("properties").jsonObject

    private fun JsonObject.required(): List<String> = getValue("required").jsonArray.map { (it as JsonPrimitive).content }
}
