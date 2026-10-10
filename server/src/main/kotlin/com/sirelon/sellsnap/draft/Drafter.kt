package com.sirelon.sellsnap.draft

import com.sirelon.sellsnap.brand.BrandContext
import com.sirelon.sellsnap.llm.LlmClient
import com.sirelon.sellsnap.llm.LlmRequest
import com.sirelon.sellsnap.spend.UsageTracker
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The drafter's JSON answer. [replyText] is the link reply of an update post; blank counts as none. */
@Serializable
class SlotDraft(
    val text: String,
    val replyText: String? = null,
    val claimedFeatureIds: List<String> = emptyList(),
)

internal val draftJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** Asks the draft model for one post. Every call is counted in [UsageTracker]; a model at its daily limit is not called. */
class Drafter(
    llm: LlmClient,
    usage: UsageTracker,
    private val brand: BrandContext,
    private val model: String,
) {
    private val caller = LlmCaller(llm, usage)

    suspend fun draft(brief: SlotBrief, rejection: Rejection? = null): LlmStep<SlotDraft> {
        val request = LlmRequest(
            model = model,
            system = Prompts.drafterSystem(brand, brief.language),
            user = Prompts.drafterUser(brief, rejection),
            responseSchema = Prompts.drafterSchema,
            maxOutputTokens = MAX_OUTPUT_TOKENS,
            thinkingLevel = THINKING_LEVEL,
        )
        return caller.call(request) { answer ->
            val draft = draftJson.decodeFromString<SlotDraft>(answer)
            SlotDraft(draft.text.trim(), draft.replyText?.trim()?.takeIf { it.isNotEmpty() }, draft.claimedFeatureIds)
        }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 3000
        const val THINKING_LEVEL = "low"
    }
}
