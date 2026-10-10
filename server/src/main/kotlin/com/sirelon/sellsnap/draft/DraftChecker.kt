package com.sirelon.sellsnap.draft

import com.sirelon.sellsnap.brand.BrandContext
import com.sirelon.sellsnap.llm.LlmClient
import com.sirelon.sellsnap.llm.LlmRequest
import com.sirelon.sellsnap.spend.UsageTracker
import kotlinx.serialization.Serializable

/** The checker's JSON answer. A Polish check also returns [backTranslationEn] and [naturalnessOk]. */
@Serializable
class CheckResult(
    val detectedLanguage: String,
    val unsupportedClaims: List<String> = emptyList(),
    val guardrailFindings: List<String> = emptyList(),
    val backTranslationEn: String? = null,
    val naturalnessOk: Boolean? = null,
    val naturalnessIssues: List<String> = emptyList(),
) {
    /**
     * Every reason the draft is not clean, in plain words; empty when it passes. A wrong language, an
     * unsupported claim or a guardrail finding fails every draft. A Polish draft also fails when its
     * naturalness verdict is not "ok" or missing, and when the back-translation is missing (D17).
     */
    fun findings(language: String): List<String> = buildList {
        if (!languageMatches(language)) add("The text is written in \"$detectedLanguage\", not in $language.")
        unsupportedClaims.forEach { add("Unsupported product claim: $it") }
        guardrailFindings.forEach { add("Guardrail: $it") }
        if (language == "pl") {
            if (naturalnessOk != true) {
                val issues = naturalnessIssues.joinToString("; ").ifEmpty { "no verdict returned" }
                add("The Polish does not read as natural: $issues")
            }
            if (backTranslationEn.isNullOrBlank()) add("No English back-translation was returned.")
        }
    }

    private fun languageMatches(language: String): Boolean =
        detectedLanguage.trim().lowercase().substringBefore('-').substringBefore('_') == language
}

/** Asks the check model to review one draft. Every call is counted in [UsageTracker]; a model at its daily limit is not called. */
class DraftChecker(
    llm: LlmClient,
    usage: UsageTracker,
    private val brand: BrandContext,
    private val model: String,
) {
    private val caller = LlmCaller(llm, usage)

    suspend fun check(brief: SlotBrief, draft: SlotDraft): LlmStep<CheckResult> {
        val request = LlmRequest(
            model = model,
            system = Prompts.checkerSystem(brand, brief.language),
            user = Prompts.checkerUser(brief, draft.text, draft.replyText, draft.claimedFeatureIds),
            responseSchema = Prompts.checkerSchema,
            maxOutputTokens = MAX_OUTPUT_TOKENS,
            thinkingLevel = THINKING_LEVEL,
        )
        return caller.call(request) { answer -> draftJson.decodeFromString<CheckResult>(answer) }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 3000
        const val THINKING_LEVEL = "low"
    }
}
