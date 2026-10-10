package com.sirelon.sellsnap.llm

import kotlinx.serialization.json.JsonObject

/** Language-model port. Every failure is an [LlmFailure] with a fixed message that never holds a key. */
interface LlmClient {
    suspend fun complete(request: LlmRequest): LlmResult
}

/**
 * One single-turn call. [responseSchema] is a JSON Schema (lowercase type names such as "object");
 * when set the reply is JSON that follows it. [maxOutputTokens] counts thinking tokens too, so a
 * low [thinkingLevel] keeps the budget for the answer.
 */
class LlmRequest(
    val model: String,
    val system: String,
    val user: String,
    val responseSchema: JsonObject? = null,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    val thinkingLevel: String = DEFAULT_THINKING_LEVEL,
) {
    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 2000
        const val DEFAULT_THINKING_LEVEL = "low"
    }
}

/**
 * [finishReason] is the API's value, for example `STOP` or `MAX_TOKENS`. A `MAX_TOKENS` result holds
 * a cut-off text, so [isComplete] is false and the caller takes its regenerate path.
 */
class LlmResult(val text: String, val finishReason: String, val usage: LlmUsage) {
    val isComplete: Boolean get() = finishReason == FINISH_STOP

    companion object {
        const val FINISH_STOP = "STOP"
        const val FINISH_MAX_TOKENS = "MAX_TOKENS"
    }
}

/** Token counts of one call. [outputTokens] excludes [thoughtsTokens]; the API reports them apart. */
class LlmUsage(
    val model: String,
    val promptTokens: Long,
    val outputTokens: Long,
    val thoughtsTokens: Long,
)

/** How the caller must react to a failed call. */
sealed class LlmFailure(message: String) : Exception(message) {
    /** HTTP 503 "high demand", 500, a timeout or a network error: retried with backoff, then the caller defers the work. */
    class Transient(message: String) : LlmFailure(message)

    /** HTTP 429: never retried at once; the caller defers the work to its next run. */
    class RateLimited(message: String) : LlmFailure(message)

    /** A safety, recitation or prohibited-content block: another attempt with the same prompt will not help. */
    class Refused(message: String) : LlmFailure(message)

    /** Any other 4xx (a bad key, a bad request, an unknown model): a configuration error to alert on. */
    class Permanent(val httpStatus: Int, message: String) : LlmFailure(message)
}
