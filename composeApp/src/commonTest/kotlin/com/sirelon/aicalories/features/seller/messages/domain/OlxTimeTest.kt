package com.sirelon.sellsnap.features.seller.messages.domain

import com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class OlxTimeTest {

    private val kyiv = TimeZone.of("Europe/Kyiv")
    private val newYork = TimeZone.of("America/New_York")

    private fun relative(raw: String, now: String, zone: TimeZone = kyiv) =
        relativeTime(raw, marketZone = kyiv, now = Instant.parse(now), zone = zone)

    @Test
    fun `every country names its market zone`() {
        assertEquals(
            listOf("Europe/Kyiv", "Europe/Warsaw", "Europe/Bucharest", "Europe/Sofia", "Europe/Lisbon", "Asia/Almaty"),
            listOf(OlxCountry.UA, OlxCountry.PL, OlxCountry.RO, OlxCountry.BG, OlxCountry.PT, OlxCountry.KZ)
                .map { it.timeZone.id },
        )
    }

    // --- parsing ---

    @Test
    fun `a wall-clock timestamp is placed on the timeline with the market zone`() {
        // Kyiv is UTC+2 in winter and UTC+3 in summer.
        assertEquals(Instant.parse("2026-10-30T19:33:53Z"), parseOlxInstant("2026-10-30 21:33:53", kyiv))
        assertEquals(Instant.parse("2026-07-01T09:00:00Z"), parseOlxInstant("2026-07-01 12:00:00", kyiv))
        assertEquals(Instant.parse("2026-07-01T11:00:00Z"), parseOlxInstant("2026-07-01 12:00:00", TimeZone.of("Europe/Lisbon")))
    }

    @Test
    fun `the spec example format parses`() {
        assertNotNull(parseOlxInstant("2018-01-30 08:26:36", kyiv))
    }

    @Test
    fun `any other format is not parsed`() {
        assertNull(parseOlxInstant("", kyiv))
        assertNull(parseOlxInstant("2026-10-30T21:33:53+02:00", kyiv))
        assertNull(parseOlxInstant("2026-13-40 99:99:99", kyiv))
        assertNull(parseOlxInstant("yesterday", kyiv))
    }

    @Test
    fun `a wall-clock time that clocks skipped over, or repeated, still parses`() {
        // Kyiv 2026-03-29: 03:00 jumps to 04:00, so 03:30 does not exist; it moves forward.
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), parseOlxInstant("2026-03-29 03:30:00", kyiv))
        // Kyiv 2026-10-25: 04:00 falls back to 03:00, so 03:30 happens twice; the earlier one wins.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), parseOlxInstant("2026-10-25 03:30:00", kyiv))
    }

    // --- relative time ---

    @Test
    fun `under a minute, or in the future, is just now`() {
        assertEquals(RelativeTime.JustNow, relative("2026-10-30 21:33:53", "2026-10-30T19:33:53Z"))
        assertEquals(RelativeTime.JustNow, relative("2026-10-30 21:33:53", "2026-10-30T19:34:52Z"))
        assertEquals(RelativeTime.JustNow, relative("2026-10-30 21:40:00", "2026-10-30T19:33:53Z"))
    }

    @Test
    fun `under an hour is minutes`() {
        assertEquals(RelativeTime.MinutesAgo(1), relative("2026-10-30 21:33:53", "2026-10-30T19:34:53Z"))
        assertEquals(RelativeTime.MinutesAgo(59), relative("2026-10-30 21:00:00", "2026-10-30T19:59:59Z"))
    }

    @Test
    fun `under a day is hours`() {
        assertEquals(RelativeTime.HoursAgo(1), relative("2026-10-30 12:00:00", "2026-10-30T11:00:00Z"))
        assertEquals(RelativeTime.HoursAgo(23), relative("2026-10-29 21:00:00", "2026-10-30T18:59:59Z"))
    }

    @Test
    fun `the calendar day before is yesterday, even a few hours into it`() {
        assertEquals(RelativeTime.Yesterday, relative("2026-10-29 10:00:00", "2026-10-30T19:00:00Z"))
        assertEquals(RelativeTime.Yesterday, relative("2026-10-29 00:00:00", "2026-10-30T19:00:00Z"))
    }

    @Test
    fun `under a week is days, and older is the date`() {
        assertEquals(RelativeTime.DaysAgo(2), relative("2026-10-28 10:00:00", "2026-10-30T19:00:00Z"))
        assertEquals(RelativeTime.DaysAgo(6), relative("2026-10-24 10:00:00", "2026-10-30T19:00:00Z"))
        assertEquals(RelativeTime.Verbatim("23.10"), relative("2026-10-23 10:00:00", "2026-10-30T19:00:00Z"))
        assertEquals(RelativeTime.Verbatim("05.01"), relative("2026-01-05 10:00:00", "2026-10-30T19:00:00Z"))
    }

    @Test
    fun `a date in another year carries the year`() {
        assertEquals(RelativeTime.Verbatim("30.10.2025"), relative("2025-10-30 21:33:53", "2026-10-30T19:00:00Z"))
        assertEquals(RelativeTime.Verbatim("31.12.2025"), relative("2025-12-31 23:00:00", "2026-01-08T10:00:00Z"))
    }

    @Test
    fun `unparseable text is kept as it came`() {
        assertEquals(RelativeTime.Verbatim(""), relative("", "2026-10-30T19:00:00Z"))
        assertEquals(RelativeTime.Verbatim("soon"), relative("soon", "2026-10-30T19:00:00Z"))
    }

    @Test
    fun `the calendar is the device's, not the market's`() {
        val raw = "2026-10-30 23:30:00"
        val now = "2026-11-01T00:30:00Z"
        // Kyiv: created on the 30th, now it is the 1st, two days on.
        assertEquals(RelativeTime.DaysAgo(2), relative(raw, now, zone = kyiv))
        // New York: created on the 30th, it is still the 31st, so one day on.
        assertEquals(RelativeTime.Yesterday, relative(raw, now, zone = newYork))
    }

    @Test
    fun `when the clocks go forward, elapsed time is real time, not the wall clock`() {
        // 23:30 on the 28th to 23:00 on the 29th looks like 23.5 hours but one was skipped.
        assertEquals(RelativeTime.HoursAgo(22), relative("2026-03-28 23:30:00", "2026-03-29T20:00:00Z"))
        assertEquals(RelativeTime.Yesterday, relative("2026-03-28 10:00:00", "2026-03-29T09:00:00Z"))
    }

    @Test
    fun `on a 25 hour day, more than a day ago can still be today`() {
        // Kyiv 2026-10-25: 00:10 EEST (+3) to 23:50 EET (+2) is 24 h 40 min on one calendar day.
        assertEquals(RelativeTime.HoursAgo(24), relative("2026-10-25 00:10:00", "2026-10-25T21:50:00Z"))
    }

    // --- message time ---

    private fun message(raw: String, now: String, zone: TimeZone = kyiv) =
        messageTime(raw, marketZone = kyiv, now = Instant.parse(now), zone = zone)

    @Test
    fun `a message from today shows the clock only`() {
        assertEquals("21:33", message("2026-10-30 21:33:53", "2026-10-30T19:50:00Z"))
        assertEquals("09:05", message("2026-10-30 09:05:00", "2026-10-30T19:50:00Z"))
    }

    @Test
    fun `a message from another day shows the date and the clock`() {
        assertEquals("30.10 21:33", message("2026-10-30 21:33:53", "2026-10-31T10:00:00Z"))
        assertEquals("05.01 08:26", message("2026-01-05 08:26:36", "2026-10-31T10:00:00Z"))
    }

    @Test
    fun `a message time is converted to the device zone`() {
        // 21:33 in Kyiv is 19:33 UTC and 15:33 in New York.
        assertEquals("19:33", message("2026-10-30 21:33:53", "2026-10-30T20:00:00Z", zone = TimeZone.UTC))
        assertEquals("15:33", message("2026-10-30 21:33:53", "2026-10-30T20:00:00Z", zone = newYork))
        // 01:30 on the 30th in Kyiv is still the evening of the 29th in New York.
        assertEquals("29.10 19:30", message("2026-10-30 01:30:00", "2026-10-30T20:00:00Z", zone = newYork))
    }

    @Test
    fun `an unparseable message time is shown as it came`() {
        assertEquals("", message("", "2026-10-30T19:00:00Z"))
        assertEquals("2026-10-30T21:33:53+02:00", message("2026-10-30T21:33:53+02:00", "2026-10-30T19:00:00Z"))
    }
}
