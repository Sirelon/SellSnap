package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.llm.LlmClient
import com.sirelon.sellsnap.llm.LlmRequest
import com.sirelon.sellsnap.llm.LlmResult
import com.sirelon.sellsnap.llm.LlmUsage
import java.util.Collections

/** Scripted [LlmClient]: answers from [replies] in order, then from [fallback]. A reply may be a result or a failure to throw. */
class FakeLlmClient(private val fallback: (LlmRequest) -> LlmResult = { request -> reply(request.model, "ok") }) : LlmClient {
    /** Synchronized: the batch drafts accounts in parallel. */
    val requests: MutableList<LlmRequest> = Collections.synchronizedList(mutableListOf())
    private val replies = ArrayDeque<Result<LlmResult>>()

    fun enqueue(result: LlmResult) {
        replies.addLast(Result.success(result))
    }

    fun enqueueFailure(failure: Throwable) {
        replies.addLast(Result.failure(failure))
    }

    override suspend fun complete(request: LlmRequest): LlmResult {
        requests += request
        return (replies.removeFirstOrNull() ?: Result.success(fallback(request))).getOrThrow()
    }

    companion object {
        fun reply(model: String, text: String, finishReason: String = LlmResult.FINISH_STOP) = LlmResult(
            text = text,
            finishReason = finishReason,
            usage = LlmUsage(model, promptTokens = 100, outputTokens = 50, thoughtsTokens = 20),
        )
    }
}
