package com.sirelon.sellsnap.features.seller.ad.generate_ad

import com.aallam.openai.api.exception.GenericIOException
import com.aallam.openai.api.exception.OpenAIHttpException
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.sirelon.sellsnap.features.seller.ad.data.IncompleteGeneratedAdException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SIR-120: which model-call failures get exactly one more try, and that it is exactly one. */
class ModelCallRetryTest {

    private val cause = RuntimeException("socket gone")

    // Stands in for io.ktor.client.engine.darwin.DarwinHttpRequestException, which only exists in
    // iosMain: the production check matches it by simple class name.
    private class DarwinHttpRequestException(message: String) : IllegalStateException(message)

    @Test
    fun timeoutsAndConnectionLossAreRetryable() {
        val retryable = listOf(
            OpenAITimeoutException(cause),
            GenericIOException(cause),
            HttpRequestTimeoutException("https://api.openai.com/v1/responses", 120_000L),
            ConnectTimeoutException("connect"),
            SocketTimeoutException("socket"),
            AdGenerationTimeoutException(),
            IncompleteGeneratedAdException(listOf("title", "suggestedPrice")),
        )

        retryable.forEach { assertTrue(it.isRetryableModelFailure(), "$it should be retried") }
    }

    @Test
    fun anNsUrlConnectionLostWrappedByOpenAiIsRetryable() {
        val darwin = DarwinHttpRequestException(
            "Exception in http request: Error Domain=NSURLErrorDomain Code=-1005 " +
                "\"The network connection was lost.\"",
        )

        assertTrue(OpenAIHttpException(darwin).isRetryableModelFailure())
    }

    @Test
    fun otherDarwinErrorsAndOtherFailuresAreNotRetried() {
        val notConnectionLost = DarwinHttpRequestException(
            "Exception in http request: Error Domain=NSURLErrorDomain Code=-1003 \"host not found\"",
        )

        assertFalse(OpenAIHttpException(notConnectionLost).isRetryableModelFailure())
        assertFalse(IllegalStateException("OpenAI request failed: invalid image").isRetryableModelFailure())
        assertFalse(CancellationException("cancelled").isRetryableModelFailure())
    }

    @Test
    fun aRetryableFailureIsRetriedOnceAndTheSecondAnswerIsReturned() = runTest {
        var calls = 0
        var retries = 0

        val result = retryModelCallOnce(onRetry = { retries++ }) {
            calls++
            if (calls == 1) throw OpenAITimeoutException(cause)
            "listing"
        }

        assertEquals("listing", result)
        assertEquals(2, calls)
        assertEquals(1, retries)
    }

    @Test
    fun aSecondFailureIsNotRetriedAgain() = runTest {
        var calls = 0
        var retries = 0

        assertFailsWith<GenericIOException> {
            retryModelCallOnce(onRetry = { retries++ }) {
                calls++
                throw GenericIOException(cause)
            }
        }

        assertEquals(2, calls, "one try plus exactly one retry")
        assertEquals(1, retries)
    }

    @Test
    fun aNonRetryableFailureIsNotRetried() = runTest {
        var calls = 0
        var retries = 0

        assertFailsWith<IllegalStateException> {
            retryModelCallOnce(onRetry = { retries++ }) {
                calls++
                error("OpenAI request failed: bad request")
            }
        }

        assertEquals(1, calls)
        assertEquals(0, retries)
    }
}
