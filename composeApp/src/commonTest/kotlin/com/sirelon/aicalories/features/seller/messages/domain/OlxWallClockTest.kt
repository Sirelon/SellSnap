package com.sirelon.sellsnap.features.seller.messages.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OlxWallClockTest {

    @Test
    fun `a space separated OLX timestamp shows as day dot month and hour minute`() {
        assertEquals("30.10 21:33", formatOlxWallClock("2026-10-30 21:33:53"))
    }

    @Test
    fun `the spec example format is trimmed the same way`() {
        assertEquals("30.01 08:26", formatOlxWallClock("2018-01-30 08:26:36"))
    }

    @Test
    fun `any other format is shown unchanged`() {
        assertEquals("2026-10-30T21:33:53+02:00", formatOlxWallClock("2026-10-30T21:33:53+02:00"))
        assertEquals("", formatOlxWallClock(""))
    }
}
