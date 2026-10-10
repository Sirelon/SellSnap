package com.sirelon.sellsnap.batch

import com.sirelon.sellsnap.content.WeekPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class IsoWeekTest {
    @Test
    fun keysAreZeroPaddedIsoWeeks() {
        assertEquals("2026-W43", IsoWeek.key(LocalDate.of(2026, 10, 19)))
        assertEquals("2026-W05", IsoWeek.key(LocalDate.of(2026, 1, 28)))
        assertEquals("2026-W01", IsoWeek.key(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun theIsoYearCanDifferFromTheCalendarYear() {
        assertEquals("2026-W01", IsoWeek.key(LocalDate.of(2025, 12, 29)))
        assertEquals("2026-W01", IsoWeek.key(LocalDate.of(2025, 12, 31)))
        assertEquals("2027-W01", IsoWeek.key(LocalDate.of(2027, 1, 4)))
    }

    @Test
    fun theYear2026HasAWeek53() {
        assertEquals("2026-W52", IsoWeek.key(LocalDate.of(2026, 12, 21)))
        assertEquals("2026-W53", IsoWeek.key(LocalDate.of(2026, 12, 28)))
        assertEquals("2026-W53", IsoWeek.key(LocalDate.of(2027, 1, 3)))
    }

    @Test
    fun aSundayRunDraftsTheComingWeek() {
        assertEquals(LocalDate.of(2026, 10, 19), IsoWeek.targetMonday(LocalDate.of(2026, 10, 18)))
        assertEquals("2026-W43", IsoWeek.key(IsoWeek.targetMonday(LocalDate.of(2026, 10, 18))))
    }

    @Test
    fun aRetryOnMondayOrLaterTargetsTheSameWeek() {
        val monday = LocalDate.of(2026, 10, 19)
        for (offset in 0L..5L) {
            assertEquals(monday, IsoWeek.targetMonday(monday.plusDays(offset)), "day +$offset")
        }
        assertEquals(LocalDate.of(2026, 10, 26), IsoWeek.targetMonday(LocalDate.of(2026, 10, 25)))
    }

    @Test
    fun theWeekAcrossTheNewYearHasTheRightKeys() {
        assertEquals("2026-W53", IsoWeek.key(IsoWeek.targetMonday(LocalDate.of(2026, 12, 27))))
        assertEquals("2027-W01", IsoWeek.key(IsoWeek.targetMonday(LocalDate.of(2027, 1, 3))))
    }

    @Test
    fun theDayIsTheKyivDayNotTheUtcDay() {
        // Saturday 23:59 in Kyiv is still Saturday; 00:30 on Sunday there is already Sunday.
        assertEquals(LocalDate.of(2026, 10, 19), IsoWeek.targetMonday(Instant.parse("2026-10-24T20:59:00Z")))
        assertEquals(LocalDate.of(2026, 10, 26), IsoWeek.targetMonday(Instant.parse("2026-10-24T21:30:00Z")))
        // Sunday 25 October 2026 is the day Kyiv leaves summer time, at 01:00 UTC.
        assertEquals(LocalDate.of(2026, 10, 26), IsoWeek.targetMonday(Instant.parse("2026-10-25T00:30:00Z")))
        assertEquals(LocalDate.of(2026, 10, 26), IsoWeek.targetMonday(Instant.parse("2026-10-25T01:30:00Z")))
        assertEquals("2026-W43", IsoWeek.key(Instant.parse("2026-10-24T20:59:00Z")))
        assertEquals("2026-W44", IsoWeek.key(Instant.parse("2026-10-25T22:30:00Z")))
    }

    @Test
    fun theSlotsOfTheWeekThatCrossesTheDstChangeKeepTheirLocalTime() {
        val monday = IsoWeek.targetMonday(LocalDate.of(2026, 10, 18))
        for ((zone, summer, winter) in listOf(
            Triple(ZoneId.of("Europe/Kyiv"), "05:30:00Z", "06:30:00Z"),
            Triple(ZoneId.of("Europe/Warsaw"), "06:30:00Z", "07:30:00Z"),
        )) {
            val plans = WeekPlanner.plan(zone, monday, Instant.parse("2026-10-18T06:00:00Z"), publishedCount = 0, occupied = emptyList(), releaseDue = false)
            val saturday = plans.single { it.date == LocalDate.of(2026, 10, 24) && it.slot == 1 }
            val sunday = plans.single { it.date == LocalDate.of(2026, 10, 25) && it.slot == 1 }
            assertEquals(Instant.parse("2026-10-24T$summer"), saturday.scheduledAt, zone.id)
            assertEquals(Instant.parse("2026-10-25T$winter"), sunday.scheduledAt, zone.id)
        }
    }
}
