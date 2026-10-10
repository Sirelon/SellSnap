package com.sirelon.sellsnap.draft

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.content.ContentRules.Violation
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource
import kotlinx.coroutines.CancellationException

/** What the pipeline made of one slot. Only [Draft] becomes a post. */
sealed interface DraftOutcome {
    /**
     * A post ready to be written. [needsAttention] is true when the checks found a problem that a
     * regeneration did not fix, or when a check could not run; [checkNotes] then says what, in plain words.
     */
    data class Draft(
        val text: String,
        val replyText: String?,
        val backTranslation: String?,
        val needsAttention: Boolean,
        val checkNotes: String?,
    ) : DraftOutcome

    /** The rules rejected two drafts in a row, or no usable draft came back; the slot gets no post. */
    data class DraftFailed(val reason: String) : DraftOutcome

    /** [model] reached its daily call limit before a draft existed; the caller stops drafting. */
    data class UsageStop(val model: String) : DraftOutcome

    /** The draft model was unreachable (503, 429, a bad key); the caller defers the slot to its next run. [reason] holds no secrets. */
    data class Deferred(val reason: String) : DraftOutcome
}

/**
 * One slot from brief to draft (D17, guardrails 1 and 3). The draft passes [ContentRules] and a language guard;
 * a failure regenerates once with the violations fed back, and a second failure ends in [DraftOutcome.DraftFailed].
 * A rule-clean draft then goes to the checker; a finding regenerates once with the findings fed back, and
 * a second finding ends in a draft with `needsAttention`. The check fails closed: a checker error, a refusal
 * or a usage stop never skips it, it flags the draft.
 */
class DraftPipeline(
    private val drafter: Drafter,
    private val checker: DraftChecker,
) {
    /** [publishedCount] is the account's published posts; [linkUrl] is a worst-case link URL for the length check. */
    suspend fun run(brief: SlotBrief, publishedCount: Int, linkUrl: String): DraftOutcome {
        var rejection: Rejection? = null
        var lastProblems = emptyList<String>()
        var draft: SlotDraft? = null
        for (attempt in 1..ATTEMPTS) {
            when (val step = drafter.draft(brief, rejection)) {
                is LlmStep.UsageStop -> return DraftOutcome.UsageStop(step.model)
                is LlmStep.Unavailable -> return DraftOutcome.Deferred(step.reason)
                is LlmStep.Unusable -> {
                    lastProblems = listOf(step.reason)
                    rejection = null
                }
                is LlmStep.Done -> {
                    val problems = ruleProblems(brief, step.value, publishedCount, linkUrl)
                    if (problems.isEmpty()) {
                        draft = step.value
                        break
                    }
                    lastProblems = problems
                    rejection = Rejection(step.value.text, step.value.replyText, problems)
                }
            }
        }
        if (draft == null) return DraftOutcome.DraftFailed(lastProblems.joinToString(" "))

        val first = review(brief, draft)
        if (first.findings.isEmpty()) return result(draft, first)
        if (first.checkDidNotRun) return result(draft, first)

        val regenerated = when (val step = drafter.draft(brief, Rejection(draft.text, draft.replyText, first.findings))) {
            is LlmStep.Done -> step.value
            is LlmStep.UsageStop -> return result(draft, first, "Regeneration skipped: ${step.model} reached its daily call limit.")
            is LlmStep.Unavailable -> return result(draft, first, "Regeneration skipped: ${step.reason}")
            is LlmStep.Unusable -> return result(draft, first, "Regeneration failed: ${step.reason}")
        }
        val problems = ruleProblems(brief, regenerated, publishedCount, linkUrl)
        if (problems.isNotEmpty()) {
            return result(draft, first, "The regenerated draft broke the rules, so the first draft stays: ${problems.joinToString(" ")}")
        }
        return result(regenerated, review(brief, regenerated))
    }

    private class Review(val findings: List<String>, val backTranslation: String?, val checkDidNotRun: Boolean = false)

    private suspend fun review(brief: SlotBrief, draft: SlotDraft): Review {
        val step = try {
            checker.check(brief, draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only the class name: an exception message could hold request details.
            return notRun("the checker failed with ${e::class.simpleName}")
        }
        return when (step) {
            is LlmStep.Done -> Review(
                findings = step.value.findings(brief.language),
                backTranslation = step.value.backTranslationEn?.trim()?.takeIf { it.isNotEmpty() },
            )
            is LlmStep.UsageStop -> notRun("${step.model} reached its daily call limit")
            is LlmStep.Unusable -> notRun(step.reason)
            is LlmStep.Unavailable -> notRun(step.reason)
        }
    }

    private fun notRun(why: String) = Review(listOf("The check did not run: $why."), backTranslation = null, checkDidNotRun = true)

    private fun result(draft: SlotDraft, review: Review, extraNote: String? = null): DraftOutcome.Draft {
        val notes = review.findings + listOfNotNull(extraNote)
        return DraftOutcome.Draft(
            text = draft.text,
            replyText = draft.replyText,
            backTranslation = review.backTranslation,
            needsAttention = notes.isNotEmpty(),
            checkNotes = notes.joinToString(" ").ifEmpty { null },
        )
    }

    private companion object {
        const val ATTEMPTS = 2
    }
}

/**
 * Every rule the draft breaks, as an instruction for the next attempt; empty when it is clean. Runs
 * [ContentRules.check], the owner's link rules (a link only in an update's reply, never in the text) and the
 * language guard: Polish takes no Cyrillic letter, Ukrainian keeps [ContentRules]' guard against letters of the
 * other Cyrillic alphabet and writes the AI as ШІ.
 */
internal fun ruleProblems(brief: SlotBrief, draft: SlotDraft, publishedCount: Int, linkUrl: String): List<String> {
    val problems = linkedSetOf<String>()
    val candidate = ContentRules.Candidate(
        text = draft.text,
        kind = PostKind.Text,
        pillar = brief.pillar,
        source = PostSource.Drafter,
        pollOptions = emptyList(),
        language = brief.language,
        publishedCount = publishedCount,
        linkUrl = linkUrl,
        replyText = draft.replyText,
    )
    ContentRules.check(candidate).forEach { problems += it.instruction(brief.language) }

    if (ContentRules.LINK_PLACEHOLDER in draft.text) problems += LINK_IN_TEXT
    if (brief.linkReplyAllowed) {
        if (draft.replyText == null) {
            problems += "Add replyText: a short invitation to try the app that holds the ${ContentRules.LINK_PLACEHOLDER} placeholder."
        } else if (ContentRules.LINK_PLACEHOLDER !in draft.replyText) {
            problems += "Put the ${ContentRules.LINK_PLACEHOLDER} placeholder into replyText."
        }
    } else if (draft.replyText != null) {
        problems += "Leave replyText out: this post has no link reply."
    }

    if (brief.language == "pl" && listOfNotNull(draft.text, draft.replyText).any(::hasCyrillic)) {
        problems += "Remove every Cyrillic letter. Write Polish with the Latin alphabet only."
    }
    if (brief.language == "uk" && listOfNotNull(draft.text, draft.replyText).any { LATIN_AI.containsMatchIn(it) }) {
        problems += "Write the AI as ШІ, not as the Latin AI."
    }
    return problems.toList()
}

private const val LINK_IN_TEXT = "Remove the {link} placeholder from text. A link may only appear in replyText."

private val LATIN_AI = Regex("\\bAI\\b")

private fun hasCyrillic(text: String): Boolean =
    text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.CYRILLIC }

private fun Violation.instruction(language: String): String = when (this) {
    Violation.Empty -> "Write a text; it is empty."
    Violation.TooLong -> "Shorten text: it is over ${ContentRules.MAX_LENGTH} Threads characters (an emoji counts as its UTF-8 bytes)."
    Violation.PollOptionCount, Violation.PollOptionLength -> "Write a plain text post without poll options."
    Violation.RussianLetter -> "Remove the letters that standard ${Prompts.languageName(language)} does not use."
    Violation.LinkNotAllowed, Violation.LinkTooEarly -> LINK_IN_TEXT
    Violation.RawUrl -> "Remove the raw URL from text. Write no http or www address."
    Violation.ReplyEmpty -> "Write a replyText, or leave it out."
    Violation.ReplyTooLong -> "Shorten replyText: it is over ${ContentRules.MAX_LENGTH} Threads characters once the link and the independence line are added."
    Violation.ReplyRussianLetter -> "Remove the letters that standard ${Prompts.languageName(language)} does not use from replyText."
    Violation.ReplyLinkNotAllowed -> "Remove the {link} placeholder from replyText: this post may not carry a link."
    Violation.ReplyRawUrl -> "Remove the raw URL from replyText. Write no http or www address."
}
