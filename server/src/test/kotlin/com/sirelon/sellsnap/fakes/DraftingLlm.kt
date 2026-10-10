package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.Config
import com.sirelon.sellsnap.llm.LlmFailure
import com.sirelon.sellsnap.llm.LlmRequest
import com.sirelon.sellsnap.llm.LlmResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A [FakeLlmClient] that plays the draft and the check model for the batch. A draft is a clean text in the account's
 * language that holds the slot's number, so every slot gets its own text; a check passes unless [flaggedSlots] names the
 * slot. Calls are numbered from 1, draft and check together; [failOnCall] and [failWhen] make a call fail with a
 * transient error, which the pipeline reports as a deferral.
 */
class DraftingLlm(
    private val draftModel: String = Config.DEFAULT_DRAFT_MODEL,
    private val checkModel: String = Config.DEFAULT_CHECK_MODEL,
) {
    /** Call numbers (1-based) that fail. */
    val failOnCall = mutableSetOf<Int>()

    /** Fails every call whose request matches, for example every Polish one. */
    var failWhen: (LlmRequest) -> Boolean = { false }

    /** Slot numbers (counted over all draft requests that open a slot) whose drafts are longer than a post may be, on every attempt. */
    val tooLongSlots = mutableSetOf<Int>()

    /** Slot numbers whose drafts the check flags, on every attempt. */
    val flaggedSlots = mutableSetOf<Int>()

    var calls = 0
        private set

    private var slotCounter = 0

    val client = FakeLlmClient(::respond)

    /** Requests to the draft model, in order. */
    val draftRequests: List<LlmRequest> get() = client.requests.filter { it.model == draftModel }

    /** Synchronized: the batch drafts accounts in parallel. */
    @Synchronized
    private fun respond(request: LlmRequest): LlmResult {
        calls++
        if (calls in failOnCall || failWhen(request)) throw LlmFailure.Transient("The model is busy")
        return if (request.model == draftModel) draft(request) else check(request)
    }

    private fun draft(request: LlmRequest): LlmResult {
        val polish = "LANGUAGE: Polish" in request.user
        // A draft with no rejection feedback opens a new slot; a regeneration keeps the slot of the draft it replaces.
        if ("YOUR PREVIOUS DRAFT WAS REJECTED" !in request.user) slotCounter++
        val slot = slotCounter
        val mention = "APP MENTION: allowed" in request.user || "APP MENTION: required" in request.user
        val update = "PILLAR: product update" in request.user
        val replyRequired = "LINK REPLY: required" in request.user
        val text = when {
            slot in tooLongSlots -> "a".repeat(700)
            polish -> "Wskazówka $slot: sfotografuj rzecz przy oknie, tam jest najlepsze światło${if (mention) ". SellSnap pomaga" else ""} 📸"
            else -> "Порада $slot: сфотографуйте річ біля вікна, там найкраще світло${if (mention) ". SellSnap допомагає" else ""} 📸"
        }
        val reply = if (replyRequired) (if (polish) "Wypróbuj SellSnap: {link}" else "Спробуйте SellSnap: {link}") else null
        val body = buildJsonObject {
            put("text", if (update) text + (if (polish) " (aktualizacja)" else " (оновлення)") else text)
            if (reply != null) put("replyText", reply)
            put("claimedFeatureIds", JsonArray(emptyList()))
        }
        return FakeLlmClient.reply(draftModel, body.toString())
    }

    private fun check(request: LlmRequest): LlmResult {
        val polish = "(pl)" in request.user
        val slot = SLOT_IN_TEXT.find(request.user)?.groupValues?.get(1)?.toInt()
        val findings = if (slot != null && slot in flaggedSlots) listOf("The draft breaks a checklist point") else emptyList()
        val body = buildJsonObject {
            put("detectedLanguage", if (polish) "pl" else "uk")
            put("unsupportedClaims", JsonArray(emptyList()))
            put("guardrailFindings", JsonArray(findings.map(::JsonPrimitive)))
            put("backTranslationEn", if (polish) JsonPrimitive("Tip $slot: photograph the item by the window") else JsonNull)
            put("naturalnessOk", if (polish) JsonPrimitive(true) else JsonNull)
            put("naturalnessIssues", JsonArray(emptyList()))
        }
        return FakeLlmClient.reply(checkModel, body.toString())
    }

    private companion object {
        val SLOT_IN_TEXT = Regex("POST TEXT:\\s*\\S+ (\\d+):")
    }
}
