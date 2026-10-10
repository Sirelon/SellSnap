package com.sirelon.sellsnap.draft

import com.sirelon.sellsnap.brand.BrandContext
import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.model.Pillar
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** The pillars Phase 2 drafts; a demo, a poll or a title pick needs Phase 3. */
val DRAFTABLE_PILLARS: Set<Pillar> = setOf(Pillar.Tip, Pillar.Update)

/** What the drafter needs to write one slot. [recentTexts] are the account's latest post texts, to avoid repeats. */
class SlotBrief(
    val pillar: Pillar,
    val language: String,
    val recentTexts: List<String>,
    /** The release an update announces; null for a tip. */
    val release: ReleaseNotes? = null,
    /** True when the post may carry a link reply (`{link}` in `replyText`). */
    val linkReplyAllowed: Boolean = false,
    /**
     * False for a tip that must not mention SellSnap: the batch lets a tip mention the app on every second tip only.
     * A product update always names the app, so the flag does not apply to it.
     */
    val mayMentionApp: Boolean = true,
) {
    init {
        require(pillar in DRAFTABLE_PILLARS) { "Pillar $pillar cannot be drafted yet" }
        require((pillar == Pillar.Update) == (release != null)) { "An update needs a release note and a tip has none" }
    }
}

/** A rejected draft and why, fed back to the drafter for its one regeneration. */
class Rejection(val text: String, val replyText: String?, val problems: List<String>)

/** Prompt text and JSON schemas for the drafter and the checker. Built from [BrandContext] only. */
object Prompts {
    const val RECENT_POSTS = 20
    private const val SAFE_LENGTH_HINT = 300

    private val LANGUAGE_NAMES = mapOf("en" to "English", "uk" to "Ukrainian", "pl" to "Polish")

    fun languageName(language: String): String = LANGUAGE_NAMES[language] ?: language

    fun drafterSystem(brand: BrandContext, language: String): String = buildString {
        appendLine("You write one Threads post for the SellSnap brand account, in ${languageName(language)}.")
        appendLine("SellSnap is a mobile app. You take a photo of an item and the AI writes the whole OLX listing.")
        appendLine("The readers sell second-hand things. They want one useful idea, told in a friendly voice.")
        appendLine()
        append(voiceSection(brand, language))
        appendLine()
        append(featureSection(brand, language))
        appendLine()
        append(checklist(language))
        appendLine()
        appendLine("OUTPUT")
        appendLine("Answer with one JSON object: text, replyText and claimedFeatureIds.")
        appendLine("text is the post. Keep the {link} placeholder out of it.")
        appendLine("replyText is the link reply of an update post. Leave it out for every other post.")
        appendLine("claimedFeatureIds lists the feature ids the post relies on. Use an empty list when it makes no product claim.")
    }

    fun checkerSystem(brand: BrandContext, language: String): String = buildString {
        appendLine("You review one Threads post for the SellSnap brand account. The post must be in ${languageName(language)}.")
        appendLine("You did not write it. Judge only what the text says.")
        appendLine()
        append(featureSection(brand, language))
        appendLine()
        append(checklist(language))
        appendLine()
        appendLine("YOUR TASK")
        appendLine("A. Set detectedLanguage to the ISO 639-1 code of the language the text is written in. Use \"mixed\" for more than one.")
        appendLine("B. List every product claim in the text or the reply that the feature list does not support in unsupportedClaims.")
        appendLine("   A claim about what the app does, how it works or what it costs needs a matching feature.")
        appendLine("   General selling advice is not a product claim.")
        appendLine("C. List every broken checklist point in guardrailFindings, one short sentence each, starting with the point number.")
        appendLine("   Code checks points 8 and 12. Skip them.")
        appendLine("   The {link} placeholder is allowed in a reply of an update post.")
        if (language == "pl") {
            appendLine("D. Write backTranslationEn: a faithful English translation of the text. Add the reply on a new line starting with \"Reply: \".")
            appendLine("E. Set naturalnessOk to true only when a native Polish speaker would find the text natural.")
            appendLine("   Name each awkward phrase, calque or wrong word in naturalnessIssues.")
        } else {
            appendLine("D. Leave backTranslationEn and naturalnessOk out. Return naturalnessIssues as an empty list.")
        }
        appendLine("Answer with one JSON object that follows the schema.")
    }

    fun drafterUser(brief: SlotBrief, rejection: Rejection? = null): String = buildString {
        appendLine("Write the post for this slot.")
        appendLine()
        appendLine("PILLAR: ${pillarName(brief.pillar)}")
        appendLine(pillarMeaning(brief.pillar))
        appendLine("LANGUAGE: ${languageName(brief.language)}")
        appendLine(appMentionLine(brief))
        appendLine(
            if (brief.linkReplyAllowed) {
                "LINK REPLY: required. Write replyText as a short invitation to try the app. It must contain the {link} placeholder once."
            } else {
                "LINK REPLY: not allowed. Leave replyText out. The post carries no link."
            },
        )
        brief.release?.let {
            appendLine()
            append(releaseSection(it, brief.language))
        }
        appendLine()
        val recent = brief.recentTexts.take(RECENT_POSTS)
        if (recent.isEmpty()) {
            appendLine("RECENT POSTS OF THIS ACCOUNT: none yet.")
        } else {
            appendLine("RECENT POSTS OF THIS ACCOUNT (newest first). Do not repeat their idea, hook or wording:")
            recent.forEachIndexed { index, text -> appendLine("${index + 1}. ${text.replace('\n', ' ')}") }
        }
        if (rejection != null) {
            appendLine()
            appendLine("YOUR PREVIOUS DRAFT WAS REJECTED.")
            appendLine("Previous text: ${rejection.text.replace('\n', ' ')}")
            rejection.replyText?.let { appendLine("Previous replyText: ${it.replace('\n', ' ')}") }
            appendLine("Fix every problem below. Write a new draft, not a patch.")
            rejection.problems.forEach { appendLine("- $it") }
        }
    }

    fun checkerUser(brief: SlotBrief, text: String, replyText: String?, claimedFeatureIds: List<String>): String = buildString {
        appendLine("Required language: ${languageName(brief.language)} (${brief.language})")
        appendLine("Pillar: ${pillarName(brief.pillar)}")
        appendLine("Feature ids the author says it relies on: ${claimedFeatureIds.ifEmpty { listOf("none") }.joinToString(", ")}")
        brief.release?.let {
            appendLine()
            appendLine("This release note is a valid source of claims for this post too.")
            append(releaseSection(it, brief.language))
        }
        appendLine()
        appendLine("POST TEXT:")
        appendLine(text)
        if (replyText != null) {
            appendLine()
            appendLine("REPLY TEXT:")
            appendLine(replyText)
        }
    }

    /** `{text, replyText?, claimedFeatureIds[]}`. */
    val drafterSchema: JsonObject = objectSchema(
        properties = mapOf(
            "text" to stringType("The post text."),
            "replyText" to stringType("The link reply of an update post. Omit for other posts."),
            "claimedFeatureIds" to stringArray("Ids of the features the post relies on."),
        ),
        required = listOf("text", "claimedFeatureIds"),
    )

    /** `{detectedLanguage, unsupportedClaims[], guardrailFindings[], backTranslationEn?, naturalnessOk?, naturalnessIssues[]}`. */
    val checkerSchema: JsonObject = objectSchema(
        properties = mapOf(
            "detectedLanguage" to stringType("ISO 639-1 code of the language the text is written in."),
            "unsupportedClaims" to stringArray("Product claims that the feature list does not support."),
            "guardrailFindings" to stringArray("Broken checklist points, one short sentence each."),
            "backTranslationEn" to stringType("English translation of a Polish text. Omit for other languages."),
            "naturalnessOk" to buildJsonObject {
                put("type", "boolean")
                put("description", "True when a native Polish speaker finds the text natural. Omit for other languages.")
            },
            "naturalnessIssues" to stringArray("Awkward phrases in a Polish text."),
        ),
        required = listOf("detectedLanguage", "unsupportedClaims", "guardrailFindings", "naturalnessIssues"),
    )

    /** Design §5 and D28 as a numbered checklist; the same list goes to the drafter and the checker. */
    fun checklist(language: String): String = buildString {
        appendLine("CHECKLIST (every point holds for every post)")
        appendLine("1. Claims: make product claims only from the feature list.")
        appendLine("2. Independence: present SellSnap as an independent app. Say nothing that suggests OLX made it, runs it or endorses it.")
        appendLine("3. Language: ${languageRule(language)}")
        appendLine("4. Data: use made-up examples. Mention no real user, photo, listing or account.")
        appendLine("5. Names: name no app, marketplace or tool except SellSnap and OLX.")
        appendLine("6. Prices: give a price only as a range, for example 200–300 zł. Never a single price.")
        appendLine("7. Links: write no raw URL (no http, no www). The {link} placeholder is allowed only in the replyText of an update post.")
        appendLine(
            "8. Length: text and replyText each stay under ${ContentRules.MAX_LENGTH} Threads characters. " +
                "An emoji counts as its UTF-8 bytes, 3 or 4 each. Aim for under $SAFE_LENGTH_HINT.",
        )
        appendLine("9. Publishing: ${publishingRule(language)}")
        appendLine("10. AI: ${aiRule(language)}")
        appendLine("11. Asks: ask for no follows, shares or likes. Promise no results.")
        appendLine("12. Fresh: do not repeat or closely paraphrase a recent post of the account.")
    }

    private fun languageRule(language: String): String = when (language) {
        "uk" -> "write every word in standard Ukrainian. Use no other Cyrillic language."
        "pl" -> "write every word in Polish with the Latin alphabet. No Cyrillic letter appears anywhere."
        else -> "write every word in ${languageName(language)}."
    }

    private fun publishingRule(language: String): String = when (language) {
        "uk" -> "say \"опублікувати\" or \"розмістити\" for putting a listing online. Use no broadcast or TV word such as \"ефір\" or \"трансляція\"."
        "pl" -> "say \"opublikować\" or \"dodać\" for putting a listing online. Use no broadcast or TV word such as \"na antenie\" or \"emisja\"."
        else -> "say \"publish\" for putting a listing online. Use no broadcast or TV word."
    }

    private fun aiRule(language: String): String = when (language) {
        "uk" -> "write the AI as \"ШІ\". Never write the Latin \"AI\"."
        "pl" -> "write the AI as \"AI\"."
        else -> "write the AI as \"AI\"."
    }

    private fun voiceSection(brand: BrandContext, language: String): String = buildString {
        appendLine("VOICE")
        appendLine("Write in the app's own voice: playful, warm, conversational. Emoji are welcome, a few per post.")
        appendLine("Samples in ${languageName(language)} (match this voice):")
        brand.voiceSamplesIn(language).forEach { appendLine("- ${it.text}") }
        if (language != "en") {
            appendLine("The same voice in English (for reference; never copy it into the post):")
            brand.voiceSamplesIn("en").forEach { appendLine("- ${it.text}") }
        }
    }

    private fun featureSection(brand: BrandContext, language: String): String = buildString {
        appendLine("FEATURES THE APP HAS (the only product claims allowed)")
        appendLine("In ${languageName(language)}:")
        brand.featuresIn(language).forEach { appendLine("${it.id}: ${it.text}") }
        if (language != "en") {
            appendLine("In English:")
            brand.featuresIn("en").forEach { appendLine("${it.id}: ${it.text}") }
        }
    }

    private fun releaseSection(release: ReleaseNotes, language: String): String = buildString {
        appendLine("RELEASE ${release.version} (${release.date})")
        release.changes.forEach { change ->
            val title = change.title[language] ?: change.title["en"].orEmpty()
            val summary = change.summary[language] ?: change.summary["en"].orEmpty()
            appendLine("- ${change.id}: $title. $summary")
        }
    }

    private fun pillarName(pillar: Pillar): String = when (pillar) {
        Pillar.Tip -> "selling tip"
        Pillar.Update -> "product update"
        else -> error("Pillar $pillar cannot be drafted yet")
    }

    /** Whether the post may name the app: an update always does, a tip only when the batch allows it. */
    private fun appMentionLine(brief: SlotBrief): String = when {
        brief.pillar == Pillar.Update -> "APP MENTION: required. The update is about SellSnap, so name it."
        brief.mayMentionApp ->
            "APP MENTION: allowed. Name SellSnap in at most one short phrase, and only with a claim from the feature list."
        else -> "APP MENTION: not allowed. Do not write SellSnap or name the app. Give the selling idea alone."
    }

    private fun pillarMeaning(pillar: Pillar): String = when (pillar) {
        Pillar.Tip ->
            "A selling tip: one practical idea that helps someone sell a second-hand item faster or for a better price " +
                "(photos, light, honest condition, price, a clear title, quick replies). It is useful without the app."
        Pillar.Update ->
            "A product update: tell the readers what is new in the release below, in your own words. " +
                "Lead with the benefit to a seller. Name only changes that the release lists."
        else -> error("Pillar $pillar cannot be drafted yet")
    }

    private fun objectSchema(properties: Map<String, JsonObject>, required: List<String>): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
        put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }

    private fun stringType(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun stringArray(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        putJsonObject("items") { put("type", "string") }
    }
}
