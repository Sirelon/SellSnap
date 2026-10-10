package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekPlannerTest {
    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val monday = LocalDate.of(2026, 10, 19)
    private val now = Instant.parse("2026-10-18T07:00:00Z")

    private fun plan(
        zone: ZoneId = kyiv,
        weekStart: LocalDate = monday,
        publishedCount: Int = 12,
        occupied: Collection<Instant> = emptyList(),
        releaseDue: Boolean = false,
    ) = WeekPlanner.plan(zone, weekStart, now, publishedCount, occupied, releaseDue)

    private fun slot(zone: ZoneId, date: LocalDate, time: LocalTime): Instant = date.atTime(time).atZone(zone).toInstant()

    @Test
    fun aRegularWeekHasSevenPostsInSlotOneOnly() {
        val plans = plan()

        assertEquals(7, plans.size)
        assertEquals((0L..6L).map { monday.plusDays(it) }, plans.map { it.date })
        assertTrue(plans.all { it.slot == 1 && it.time == SlotPlanner.SLOT_1 })
        assertTrue(plans.all { it.pillar == Pillar.Tip && it.kind == PostKind.Text && !it.linkReplyAllowed })
    }

    @Test
    fun wednesdayIsATipAndThursdaySlotTwoStaysEmpty() {
        val plans = plan(publishedCount = 0)

        val wednesday = plans.single { it.date.dayOfWeek == DayOfWeek.WEDNESDAY && it.slot == 1 }
        assertEquals(Pillar.Tip, wednesday.pillar)
        assertEquals(PostKind.Text, wednesday.kind)
        assertTrue(plans.none { it.pillar == Pillar.Poll || it.pillar == Pillar.Reveal })

        val regular = plan()
        assertTrue(regular.none { it.date.dayOfWeek == DayOfWeek.THURSDAY && it.slot == 2 })
    }

    @Test
    fun aLaunchWeekAddsSlotTwoUntilTheProjectedCountReachesTen() {
        val plans = plan(publishedCount = 0)

        // Mon-Fri hold two posts each (10 posts); then the count is 10, so Saturday and Sunday have slot 1 only.
        assertEquals(12, plans.size)
        val perDay = plans.groupingBy { it.date.dayOfWeek }.eachCount()
        assertEquals(
            mapOf(
                DayOfWeek.MONDAY to 2, DayOfWeek.TUESDAY to 2, DayOfWeek.WEDNESDAY to 2, DayOfWeek.THURSDAY to 2,
                DayOfWeek.FRIDAY to 2, DayOfWeek.SATURDAY to 1, DayOfWeek.SUNDAY to 1,
            ),
            perDay,
        )
        assertTrue(plans.filter { it.slot == 2 }.all { it.time == SlotPlanner.SLOT_2 && it.pillar == Pillar.Tip })
    }

    @Test
    fun aLaunchWeekStartingNearTenStopsEarly() {
        // 8 published: Monday slot 1 makes 9, Monday slot 2 makes 10, then slot 1 only.
        val plans = plan(publishedCount = 8)

        assertEquals(8, plans.size)
        assertEquals(listOf(1, 2), plans.filter { it.date == monday }.map { it.slot })
        assertTrue(plans.filter { it.date != monday }.all { it.slot == 1 })
    }

    @Test
    fun afterLaunchSlotTwoStaysEmpty() {
        assertTrue(plan(publishedCount = 10).none { it.slot == 2 })
        assertTrue(plan(publishedCount = 500).none { it.slot == 2 })
    }

    @Test
    fun scheduledFutureProjectsIntoTheLaunchCount() {
        // 9 posts already scheduled for next week: only one more fits before the count reaches ten.
        val nextWeek = (0L..8L).map { slot(kyiv, monday.plusDays(10 + it), SlotPlanner.SLOT_1) }

        val plans = plan(publishedCount = 0, occupied = nextWeek)

        assertEquals(7, plans.size)
        assertTrue(plans.none { it.slot == 2 })
    }

    @Test
    fun occupiedPastSlotsDoNotCountAsScheduledFuture() {
        val past = (1L..4L).map { slot(kyiv, LocalDate.of(2026, 10, 10).plusDays(it), SlotPlanner.SLOT_1) }

        // 8 published already include those past posts; only 8 count, so Monday slot 2 is planned.
        val plans = plan(publishedCount = 8, occupied = past)

        assertEquals(listOf(1, 2), plans.filter { it.date == monday }.map { it.slot })
    }

    @Test
    fun anOccupiedTemplateSlotIsSkipped() {
        val tuesdayOne = slot(kyiv, monday.plusDays(1), SlotPlanner.SLOT_1)

        val plans = plan(occupied = listOf(tuesdayOne))

        assertEquals(6, plans.size)
        assertTrue(plans.none { it.date == monday.plusDays(1) })
    }

    @Test
    fun theDailyCapCountsOccupiedPostsOffTheTemplateSlots() {
        val wednesday = monday.plusDays(2)
        val thursday = monday.plusDays(3)
        val occupied = listOf(
            slot(kyiv, wednesday, LocalTime.of(10, 0)),
            slot(kyiv, wednesday, LocalTime.of(12, 0)),
            slot(kyiv, thursday, LocalTime.of(12, 0)),
        )

        val plans = plan(publishedCount = 0, occupied = occupied)

        assertTrue(plans.none { it.date == wednesday })
        assertEquals(1, plans.count { it.date == thursday })
        assertTrue(plans.groupingBy { it.date }.eachCount().values.all { it <= SlotPlanner.MAX_PER_DAY })
    }

    @Test
    fun aSundayReleaseIsAnUpdateWithALinkReply() {
        val plans = plan(releaseDue = true)

        val sunday = plans.single { it.date.dayOfWeek == DayOfWeek.SUNDAY }
        assertEquals(Pillar.Update, sunday.pillar)
        assertTrue(sunday.linkReplyAllowed)
        assertEquals(1, plans.count { it.pillar == Pillar.Update })
        assertTrue(plans.filter { it.pillar != Pillar.Update }.none { it.linkReplyAllowed })
    }

    @Test
    fun withoutAReleaseSundayIsATip() {
        val sunday = plan(releaseDue = false).single { it.date.dayOfWeek == DayOfWeek.SUNDAY }

        assertEquals(Pillar.Tip, sunday.pillar)
        assertFalse(sunday.linkReplyAllowed)
    }

    @Test
    fun onlySundaySlotOneCanBeAnUpdate() {
        val plans = plan(publishedCount = 0, releaseDue = true)

        assertEquals(listOf(DayOfWeek.SUNDAY to 1), plans.filter { it.pillar == Pillar.Update }.map { it.date.dayOfWeek to it.slot })
    }

    @Test
    fun anOccupiedSundaySlotOneMeansNoUpdate() {
        val sundayOne = slot(kyiv, monday.plusDays(6), SlotPlanner.SLOT_1)

        val plans = plan(occupied = listOf(sundayOne), releaseDue = true)

        assertTrue(plans.none { it.pillar == Pillar.Update })
    }

    @Test
    fun theKyivWeekAcrossTheClockChangeKeepsLocalSlotTimes() {
        // Europe/Kyiv leaves summer time on Sunday 2026-10-25 at 04:00 local.
        val plans = plan(zone = kyiv, publishedCount = 0, weekStart = LocalDate.of(2026, 10, 24))

        val saturday = plans.filter { it.date == LocalDate.of(2026, 10, 24) }
        val sunday = plans.filter { it.date == LocalDate.of(2026, 10, 25) }
        assertEquals(listOf("2026-10-24T05:30:00Z", "2026-10-24T16:30:00Z"), saturday.map { it.scheduledAt.toString() })
        assertEquals(listOf("2026-10-25T06:30:00Z", "2026-10-25T17:30:00Z"), sunday.map { it.scheduledAt.toString() })
        plans.forEach { assertEquals(it.time, it.scheduledAt.atZone(kyiv).toLocalTime()) }
    }

    @Test
    fun theWarsawWeekAcrossTheClockChangeKeepsLocalSlotTimes() {
        // Europe/Warsaw leaves summer time on the same Sunday at 03:00 local.
        val plans = plan(zone = warsaw, publishedCount = 0, weekStart = LocalDate.of(2026, 10, 24))

        val saturday = plans.filter { it.date == LocalDate.of(2026, 10, 24) }
        val sunday = plans.filter { it.date == LocalDate.of(2026, 10, 25) }
        assertEquals(listOf("2026-10-24T06:30:00Z", "2026-10-24T17:30:00Z"), saturday.map { it.scheduledAt.toString() })
        assertEquals(listOf("2026-10-25T07:30:00Z", "2026-10-25T18:30:00Z"), sunday.map { it.scheduledAt.toString() })
        plans.forEach { assertEquals(it.time, it.scheduledAt.atZone(warsaw).toLocalTime()) }
    }

    @Test
    fun aRegularWeekThatEndsOnTheClockChangeDayKeepsSundayAtLocalEightThirty() {
        val sunday = plan(zone = kyiv, weekStart = LocalDate.of(2026, 10, 19)).single { it.date == LocalDate.of(2026, 10, 25) }
        val warsawSunday = plan(zone = warsaw, weekStart = LocalDate.of(2026, 10, 19)).single { it.date == LocalDate.of(2026, 10, 25) }

        assertEquals(Instant.parse("2026-10-25T06:30:00Z"), sunday.scheduledAt)
        assertEquals(Instant.parse("2026-10-25T07:30:00Z"), warsawSunday.scheduledAt)
    }
}
