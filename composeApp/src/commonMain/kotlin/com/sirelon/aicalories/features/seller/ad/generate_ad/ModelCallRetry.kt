package com.sirelon.sellsnap.features.seller.ad.generate_ad

import com.aallam.openai.api.exception.GenericIOException
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.sirelon.sellsnap.features.seller.ad.data.IncompleteGeneratedAdException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

private const val DarwinRequestException = "DarwinHttpRequestException"

// NSURLErrorNetworkConnectionLost: the connection dropped mid-request.
private const val NsUrlErrorConnectionLost = "Code=-1005"

/**
 * Whether a failed model call is worth exactly one more try: a timeout, a lost connection, or an
 * answer without a title or price. None of these says the request itself was wrong, so the same
 * photo URLs go out again. A 4xx other than 429 is the request being wrong, and 429 is already
 * waited out inside the OpenAI client (see `createOpenAI`), so neither is listed.
 *
 * openai-kotlin wraps Ktor's timeouts as [OpenAITimeoutException] and any `IOException` as
 * [GenericIOException]; the raw Ktor types are listed too in case a wrapper is ever skipped.
 * The Darwin engine's exception is not an `IOException`, so it reaches us as an
 * `OpenAIHttpException` with the Darwin exception as its cause. It lives in `iosMain`, so it is
 * matched by class name and by the NSURLError code in its message. Inferred: that message carries
 * `Code=-1005` the way NSError's description does.
 */
internal fun Throwable.isRetryableModelFailure(): Boolean = when {
    this is CancellationException -> false
    this is OpenAITimeoutException ||
        this is GenericIOException ||
        this is HttpRequestTimeoutException ||
        this is ConnectTimeoutException ||
        this is SocketTimeoutException ||
        this is AdGenerationTimeoutException ||
        this is IncompleteGeneratedAdException -> true

    else -> generateSequence(this) { it.cause }.any { it.isDarwinConnectionLost() }
}

private fun Throwable.isDarwinConnectionLost(): Boolean =
    this::class.simpleName == DarwinRequestException &&
        message.orEmpty().contains(NsUrlErrorConnectionLost)

/**
 * Runs [call] and, if it fails with something [isRetryableModelFailure] accepts, runs it once
 * more, calling [onRetry] first. A second failure propagates as it is.
 */
internal suspend fun <T> retryModelCallOnce(onRetry: () -> Unit, call: suspend () -> T): T =
    try {
        call()
    } catch (error: Throwable) {
        if (!error.isRetryableModelFailure()) throw error
        onRetry()
        call()
    }
