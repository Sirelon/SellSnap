package com.sirelon.sellsnap.spend

import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.llm.LlmUsage
import com.sirelon.sellsnap.model.UsageCounters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageTrackerTest {
    private val store = InMemorySocialStore()
    private val clock = MutableClock(Instant.parse("2026-10-20T23:30:00Z"))
    private val tracker = UsageTracker(store, clock, dailyCallLimit = 200)

    private fun usage(model: String = DRAFT, prompt: Long = 100, output: Long = 50, thoughts: Long = 20) =
        LlmUsage(model, prompt, output, thoughts)

    @Test
    fun recordsCountersPerModelUnderTheUtcDay() = runTest {
        tracker.record(usage(DRAFT, 100, 50, 20))
        tracker.record(usage(DRAFT, 10, 5, 2))
        tracker.record(usage(CHECK, 7, 3, 0))

        assertEquals(
            mapOf(
                DRAFT to UsageCounters(calls = 2, promptTokens = 110, outputTokens = 55, thoughtsTokens = 22),
                CHECK to UsageCounters(calls = 1, promptTokens = 7, outputTokens = 3, thoughtsTokens = 0),
            ),
            store.allUsage.getValue("20261020"),
        )
    }

    @Test
    fun aNewUtcDayStartsFromZero() = runTest {
        tracker.record(usage())
        clock.now = Instant.parse("2026-10-21T00:00:01Z")

        assertEquals(0, tracker.callsToday(DRAFT))
        tracker.record(usage())

        assertEquals(1, tracker.callsToday(DRAFT))
        assertEquals(1, store.allUsage.getValue("20261020").getValue(DRAFT).calls)
        assertEquals(1, store.allUsage.getValue("20261021").getValue(DRAFT).calls)
    }

    @Test
    fun concurrentCallsLoseNoIncrement() = runTest {
        (1..50).map { async { tracker.record(usage()) } }.awaitAll()

        assertEquals(50, tracker.callsToday(DRAFT))
        assertEquals(5_000, store.usageOf("20261020").getValue(DRAFT).promptTokens)
    }

    @Test
    fun canCallStopsAtNinetyPercentOfTheLimitPerModel() = runTest {
        repeat(179) { tracker.record(usage()) }
        assertTrue(tracker.canCall(DRAFT))

        tracker.record(usage())

        assertEquals(180, tracker.callsToday(DRAFT))
        assertFalse(tracker.canCall(DRAFT))
        assertTrue(tracker.canCall(CHECK))
    }

    @Test
    fun aTinyLimitStillAllowsTheFirstCallsBelowNinetyPercent() = runTest {
        val small = UsageTracker(store, clock, dailyCallLimit = 10)
        repeat(8) { small.record(usage()) }
        assertTrue(small.canCall(DRAFT))

        small.record(usage())

        assertFalse(small.canCall(DRAFT))
    }

    private companion object {
        const val DRAFT = "gemini-3.8-flash"
        const val CHECK = "gemini-3.5-flash-lite"
    }
}
