package com.sirelon.sellsnap.threads

import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Pauses between the attempts of one call: three retries after the first try. */
val DEFAULT_RETRY_DELAYS: List<Duration> = listOf(1.seconds, 2.seconds, 4.seconds)

/** Graph API error codes for rate limits; they are never retried and never pause an account. */
val RATE_LIMIT_CODES: Set<Int> = setOf(4, 17, 32, 613)

/**
 * Maps a failed HTTP response to a [ThreadsFailure] with a fixed message. Rate-limit codes win
 * over `is_transient`, because retrying a rate limit makes it worse. A bare HTTP 429 is a rate limit.
 * Everything else below 500 is permanent, including code 190 and policy blocks such as 368.
 */
fun classifyResponse(httpStatus: Int, code: Int?, isTransient: Boolean): ThreadsFailure {
    val codeText = code?.let { ", code $it" }.orEmpty()
    return when {
        code in RATE_LIMIT_CODES || httpStatus == 429 ->
            ThreadsFailure.RateLimited("Threads rate limit reached (HTTP $httpStatus$codeText)")
        isTransient ->
            ThreadsFailure.Transient("Threads reported a temporary error (HTTP $httpStatus$codeText)")
        httpStatus >= 500 ->
            ThreadsFailure.Transient("Threads server error (HTTP $httpStatus$codeText)")
        else ->
            ThreadsFailure.Permanent(code, "Threads rejected the request (HTTP $httpStatus$codeText)")
    }
}

/** True when [failure] is worth another attempt with backoff. */
fun isRetryable(failure: Throwable): Boolean = failure is ThreadsFailure.Transient || failure is IOException

/**
 * Runs [block] and retries it after each delay in [delays] while it fails transiently. [block]
 * receives the attempt number, starting at 0, so a step that is not safe to repeat blindly can
 * check state first. The last transient failure is rethrown; rate-limit and permanent failures
 * are rethrown at once. A raw [IOException] leaves as a [ThreadsFailure.Transient] with a fixed message.
 */
suspend fun <T> withRetry(
    delays: List<Duration> = DEFAULT_RETRY_DELAYS,
    block: suspend (attempt: Int) -> T,
): T = withRetryIf(delays, ::isRetryable, block)

/**
 * [withRetry] for a caller with its own failure type: [block] is retried while [retryable] accepts
 * the failure, and the last failure is rethrown as it is, except that an [IOException] becomes a
 * [ThreadsFailure.Transient].
 */
suspend fun <T> withRetryIf(
    delays: List<Duration>,
    retryable: (Throwable) -> Boolean,
    block: suspend (attempt: Int) -> T,
): T {
    var attempt = 0
    while (true) {
        try {
            return block(attempt)
        } catch (failure: Throwable) {
            if (!retryable(failure) || attempt >= delays.size) throw failure.asThreadsFailure()
            delay(delays[attempt])
            attempt++
        }
    }
}

private fun Throwable.asThreadsFailure(): Throwable =
    if (this is IOException) ThreadsFailure.Transient("Threads request failed (network error)") else this
