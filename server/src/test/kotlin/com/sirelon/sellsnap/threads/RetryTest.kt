@file:OptIn(ExperimentalCoroutinesApi::class)

package com.sirelon.sellsnap.threads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RetryTest {
    @Test
    fun serverErrorsAndTemporaryErrorsAreTransient() {
        assertIs<ThreadsFailure.Transient>(classifyResponse(500, null, false))
        assertIs<ThreadsFailure.Transient>(classifyResponse(503, 2, false))
        assertIs<ThreadsFailure.Transient>(classifyResponse(400, 1, isTransient = true))
    }

    @Test
    fun rateLimitCodesAreRateLimitedEvenWhenFlaggedTransient() {
        for (code in listOf(4, 17, 32, 613)) {
            assertIs<ThreadsFailure.RateLimited>(classifyResponse(400, code, false), "code $code")
            assertIs<ThreadsFailure.RateLimited>(classifyResponse(400, code, true), "code $code transient")
        }
        assertIs<ThreadsFailure.RateLimited>(classifyResponse(429, null, false))
    }

    @Test
    fun tokenErrorsPolicyBlocksAndOtherClientErrorsArePermanent() {
        for ((status, code) in listOf(400 to 190, 401 to 190, 400 to 368, 400 to 100, 403 to 10, 404 to null, 400 to null)) {
            val failure = assertIs<ThreadsFailure.Permanent>(classifyResponse(status, code, false), "$status/$code")
            assertEquals(code, failure.code)
        }
    }

    @Test
    fun classifiedMessagesCarryOnlyStatusAndCode() {
        assertEquals("Threads rejected the request (HTTP 400, code 190)", classifyResponse(400, 190, false).message)
        assertEquals("Threads server error (HTTP 502)", classifyResponse(502, null, false).message)
    }

    @Test
    fun retriesThreeTimesWithOneTwoAndFourSeconds() = runTest {
        var calls = 0
        val result = withRetry { attempt ->
            calls++
            if (attempt < 3) throw ThreadsFailure.Transient("temporary") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(4, calls)
        assertEquals(7_000, currentTime)
    }

    @Test
    fun givesUpAfterTheThirdRetryAndRethrowsTheTransientFailure() = runTest {
        var calls = 0
        assertFailsWith<ThreadsFailure.Transient> {
            withRetry {
                calls++
                throw ThreadsFailure.Transient("temporary")
            }
        }
        assertEquals(4, calls)
        assertEquals(7_000, currentTime)
    }

    @Test
    fun waitsExactlyOneThenTwoThenFourSeconds() = runTest {
        val times = mutableListOf<Long>()
        assertFailsWith<ThreadsFailure.Transient> {
            withRetry {
                times += currentTime
                throw ThreadsFailure.Transient("temporary")
            }
        }
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), times)
    }

    @Test
    fun permanentFailuresAreNotRetried() = runTest {
        var calls = 0
        val failure = assertFailsWith<ThreadsFailure.Permanent> {
            withRetry {
                calls++
                throw ThreadsFailure.Permanent(190, "token")
            }
        }
        assertEquals(190, failure.code)
        assertEquals(1, calls)
        assertEquals(0, currentTime)
    }

    @Test
    fun rateLimitsAreNotRetried() = runTest {
        var calls = 0
        assertFailsWith<ThreadsFailure.RateLimited> {
            withRetry {
                calls++
                throw ThreadsFailure.RateLimited("slow down")
            }
        }
        assertEquals(1, calls)
        assertEquals(0, currentTime)
    }

    @Test
    fun anIoExceptionIsRetriedAndLeavesAsATransientFailureWithAFixedMessage() = runTest {
        var calls = 0
        val failure = assertFailsWith<ThreadsFailure.Transient> {
            withRetry {
                calls++
                throw IOException("https://graph.threads.net/v1.0/me?access_token=SECRET")
            }
        }
        assertEquals(4, calls)
        assertTrue("SECRET" !in failure.message.orEmpty())
        assertEquals(null, failure.cause)
    }

    @Test
    fun unrelatedExceptionsPassThroughUntouched() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            withRetry {
                calls++
                error("bug")
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun aCustomPredicateDecidesWhatIsRetried() = runTest {
        var calls = 0
        val result = withRetryIf(listOf(1.seconds, 1.seconds), { it is IllegalStateException }) { attempt ->
            calls++
            if (attempt < 2) error("again") else "done"
        }
        assertEquals("done", result)
        assertEquals(3, calls)

        calls = 0
        assertFailsWith<ThreadsFailure.Transient> {
            withRetryIf(DEFAULT_RETRY_DELAYS, { false }) {
                calls++
                throw ThreadsFailure.Transient("not retried here")
            }
        }
        assertEquals(1, calls)
    }
}
