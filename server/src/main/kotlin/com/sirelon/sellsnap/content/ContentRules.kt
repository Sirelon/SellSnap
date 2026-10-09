package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource

/** Pure checks every post passes before it reaches the owner or Threads. */
object ContentRules {
    const val LINK_PLACEHOLDER = "{link}"

    /** Threads allows 500; 10 stay in reserve for counting differences. */
    const val MAX_LENGTH = 490

    /** Guardrail 12: no link in an account's first five posts. */
    const val MIN_PUBLISHED_BEFORE_LINK = 5

    const val POLL_MIN_OPTIONS = 2
    const val POLL_MAX_OPTIONS = 4
    const val POLL_OPTION_MAX_LENGTH = 25

    /** Pillars that may carry `{link}`; owner `/post` posts may too. */
    val LINK_PILLARS: Set<Pillar> = setOf(Pillar.Update)

    /** First sentence of the store-listing independence statement, per language. */
    val INDEPENDENCE_LINE: Map<String, String> = mapOf(
        "en" to "SellSnap is an independent app and is not affiliated with or endorsed by OLX.",
        "uk" to "SellSnap — незалежний застосунок, не афілійований з OLX.",
        "pl" to "SellSnap to niezależna aplikacja, niepowiązana z OLX.",
    )

    // Letters of the russian alphabet that Ukrainian and Polish do not use (guardrail 3).
    private const val RUSSIAN_ONLY_LETTERS = "ыэъёЫЭЪЁ"

    private val RAW_URL = Regex("(?:https?://|www\\.)", RegexOption.IGNORE_CASE)

    enum class Violation {
        Empty,
        TooLong,
        PollOptionCount,
        PollOptionLength,
        RussianLetter,
        LinkNotAllowed,
        LinkTooEarly,
        RawUrl,
    }

    class Candidate(
        val text: String,
        val kind: PostKind,
        val pillar: Pillar,
        val source: PostSource,
        val pollOptions: List<String>,
        val language: String,
        /** Posts the account has published so far. */
        val publishedCount: Int,
        /** Redirect URL that replaces `{link}`. */
        val linkUrl: String,
    )

    fun check(candidate: Candidate): Set<Violation> = with(candidate) {
        val violations = linkedSetOf<Violation>()
        val optionTexts = if (kind == PostKind.Poll) pollOptions else emptyList()
        val allTexts = listOf(text) + optionTexts

        if (kind != PostKind.Image && text.isBlank()) violations += Violation.Empty
        if (allTexts.any { t -> t.any { it in RUSSIAN_ONLY_LETTERS } }) violations += Violation.RussianLetter
        if (allTexts.any { RAW_URL.containsMatchIn(it) }) violations += Violation.RawUrl

        if (LINK_PLACEHOLDER in text) {
            if (pillar !in LINK_PILLARS && source != PostSource.Owner) violations += Violation.LinkNotAllowed
            if (publishedCount < MIN_PUBLISHED_BEFORE_LINK) violations += Violation.LinkTooEarly
        }
        if (optionTexts.any { LINK_PLACEHOLDER in it }) violations += Violation.LinkNotAllowed

        if (threadsLength(render(text, linkUrl, language)) > MAX_LENGTH) violations += Violation.TooLong

        if (kind == PostKind.Poll) {
            if (pollOptions.size !in POLL_MIN_OPTIONS..POLL_MAX_OPTIONS) violations += Violation.PollOptionCount
            if (pollOptions.any { it.isBlank() || it.codePointCount(0, it.length) > POLL_OPTION_MAX_LENGTH }) {
                violations += Violation.PollOptionLength
            }
        }
        violations
    }

    /** Final post text: `{link}` replaced by [linkUrl] and, for a linked post, the independence line appended. */
    fun render(text: String, linkUrl: String, language: String): String {
        if (LINK_PLACEHOLDER !in text) return text
        val line = INDEPENDENCE_LINE[language] ?: INDEPENDENCE_LINE.getValue("en")
        return text.replace(LINK_PLACEHOLDER, linkUrl).trimEnd() + "\n\n" + line
    }

    /**
     * Length as Threads counts it: an emoji counts as its UTF-8 byte length, every other code
     * point as 1. Emoji parts are Extended_Pictographic, regional indicators, skin-tone
     * modifiers, ZWJ and VS16.
     */
    fun threadsLength(text: String): Int = text.codePoints().toArray().sumOf { cp ->
        if (isEmojiPart(cp)) utf8Length(cp) else 1
    }

    private fun isEmojiPart(cp: Int): Boolean =
        cp in 0x1F1E6..0x1F1FF || cp == 0x200D || cp == 0xFE0F ||
            Character.isEmojiModifier(cp) || Character.isExtendedPictographic(cp)

    private fun utf8Length(cp: Int): Int = when {
        cp < 0x80 -> 1
        cp < 0x800 -> 2
        cp < 0x10000 -> 3
        else -> 4
    }
}
