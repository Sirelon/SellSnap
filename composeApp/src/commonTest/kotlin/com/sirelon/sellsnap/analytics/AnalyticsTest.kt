package com.sirelon.sellsnap.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsTest {

    @Test
    fun `converts a Boolean param to its string form`() {
        val normalized = mapOf("had_zero_views" to true, "was_active" to false).normalizedForFirebase()

        assertEquals("true", normalized["had_zero_views"])
        assertEquals("false", normalized["was_active"])
    }

    @Test
    fun `converts a Long param to Int so it survives on iOS`() {
        val normalized = mapOf("duration_ms" to 135_000L).normalizedForFirebase()

        assertEquals(135_000, normalized["duration_ms"])
    }

    @Test
    fun `clamps a Long param outside the Int range instead of wrapping around`() {
        val normalized = mapOf(
            "over" to (Int.MAX_VALUE.toLong() + 1),
            "under" to (Int.MIN_VALUE.toLong() - 1),
        ).normalizedForFirebase()

        assertEquals(Int.MAX_VALUE, normalized["over"])
        assertEquals(Int.MIN_VALUE, normalized["under"])
    }

    @Test
    fun `leaves String, Int and Double params untouched`() {
        val normalized = mapOf(
            "reason" to "network",
            "account_index" to 1,
            "suggested_price" to 12.5,
        ).normalizedForFirebase()

        assertEquals("network", normalized["reason"])
        assertEquals(1, normalized["account_index"])
        assertEquals(12.5, normalized["suggested_price"])
    }
}
