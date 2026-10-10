package com.sirelon.sellsnap.draft

import com.sirelon.sellsnap.llm.LlmClient
import com.sirelon.sellsnap.llm.LlmFailure
import com.sirelon.sellsnap.llm.LlmRequest
import com.sirelon.sellsnap.llm.LlmResult
import com.sirelon.sellsnap.spend.UsageTracker

/** What one model call came to. The caller decides what each non-[Done] case means for its own step. */
sealed interface LlmStep<out T> {
    data class Done<T>(val value: T) : LlmStep<T>

    /** [model] has used up its free-tier daily calls; no call was made. */
    data class UsageStop(val model: String) : LlmStep<Nothing>

    /** The model answered, but with nothing usable: a refusal, a cut-off answer or invalid JSON. */
    data class Unusable(val reason: String) : LlmStep<Nothing>

    /** The model could not be reached or rejected the call (503, 429, a bad key). [reason] is a fixed message without secrets. */
    data class Unavailable(val reason: String) : LlmStep<Nothing>
}

/** The one place a drafting call checks the usage limit, calls the model, records the usage and parses the answer. */
internal class LlmCaller(private val llm: LlmClient, private val usage: UsageTracker) {
    suspend fun <T> call(request: LlmRequest, parse: (String) -> T): LlmStep<T> {
        if (!usage.canCall(request.model)) return LlmStep.UsageStop(request.model)
        val result: LlmResult = try {
            llm.complete(request)
        } catch (failure: LlmFailure.Refused) {
            return LlmStep.Unusable(failure.message.orEmpty())
        } catch (failure: LlmFailure) {
            return LlmStep.Unavailable(failure.message.orEmpty())
        }
        usage.record(result.usage)
        if (!result.isComplete) return LlmStep.Unusable("The answer was cut off (${result.finishReason})")
        return try {
            LlmStep.Done(parse(result.text))
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException; the message is fixed, never the answer.
            LlmStep.Unusable("The answer was not valid JSON for the schema")
        }
    }
}
