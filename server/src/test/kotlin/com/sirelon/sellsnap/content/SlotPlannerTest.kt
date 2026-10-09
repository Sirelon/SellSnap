package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlotPlannerTest {
    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val warsaw = ZoneId.of("Europe/Warsaw")

    private fun next(
        now: String,
        zone: ZoneId = kyiv,
        publishedCount: Int = 0,
        slot2Only: Boolean = false,
        taken: List<Instant> = emptyList(),
        notAfter: Instant? = null,
    ) = SlotPlanner.nextFreeSlot(Instant.parse(now), zone, publishedCount, slot2Only, taken, notAfter)

    @Test
    fun slotsFollowLocalTimeInKyivAndWarsaw() {
        // Before the DST change Kyiv is UTC+3 and Warsaw UTC+2.
        assertEquals(Instant.parse("2026-10-20T05:30:00Z"), next("2026-10-20T00:00:00Z", kyiv))
        assertEquals(Instant.parse("2026-10-20T06:30:00Z"), next("2026-10-20T00:00:00Z", warsaw))
        assertEquals(Instant.parse("2026-10-20T16:30:00Z"), next("2026-10-20T10:00:00Z", kyiv))
        assertEquals(Instant.parse("2026-10-20T17:30:00Z"), next("2026-10-20T10:00:00Z", warsaw))
    }

    @Test
    fun slotsKeepLocalTimeAcrossTheDstChangeOn20261025() {
        // Both zones leave summer time at 01:00 UTC on 2026-10-25.
        val kyivDay1 = Instant.parse("2026-10-24T05:30:00Z")
        val kyivDay2 = next("2026-10-24T06:00:00Z", kyiv, publishedCount = 10)
        assertEquals(Instant.parse("2026-10-25T06:30:00Z"), kyivDay2)
        assertEquals(kyivDay1.plusSeconds(25 * 3600), kyivDay2)

        val warsawDay1 = next("2026-10-24T00:00:00Z", warsaw, publishedCount = 10)
        val warsawDay2 = next("2026-10-24T07:00:00Z", warsaw, publishedCount = 10)
        assertEquals(Instant.parse("2026-10-24T06:30:00Z"), warsawDay1)
        assertEquals(Instant.parse("2026-10-25T07:30:00Z"), warsawDay2)
    }

    @Test
    fun eveningSlotAlsoShiftsAcrossTheDstChange() {
        assertEquals(Instant.parse("2026-10-24T16:30:00Z"), next("2026-10-24T10:00:00Z", kyiv, slot2Only = true))
        assertEquals(Instant.parse("2026-10-25T17:30:00Z"), next("2026-10-25T10:00:00Z", kyiv, slot2Only = true))
        assertEquals(Instant.parse("2026-10-25T18:30:00Z"), next("2026-10-25T10:00:00Z", warsaw, slot2Only = true))
    }

    @Test
    fun launchWeekUsesBothSlotsAndLaterOnlySlotOne() {
        val morning = Instant.parse("2026-10-20T05:30:00Z")
        val evening = Instant.parse("2026-10-20T16:30:00Z")
        assertEquals(evening, next("2026-10-20T00:00:00Z", publishedCount = 9, taken = listOf(morning)))
        assertEquals(
            Instant.parse("2026-10-21T05:30:00Z"),
            next("2026-10-20T00:00:00Z", publishedCount = 10, taken = listOf(morning)),
        )
    }

    @Test
    fun pollsGoInSlotTwoEvenDuringLaunchWeek() {
        assertEquals(
            Instant.parse("2026-10-20T16:30:00Z"),
            next("2026-10-20T00:00:00Z", publishedCount = 0, slot2Only = true),
        )
        assertTrue(SlotPlanner.requiresSlot2(PostKind.Poll, Pillar.Poll))
        assertTrue(SlotPlanner.requiresSlot2(PostKind.Poll, Pillar.TitlePick))
        assertTrue(SlotPlanner.requiresSlot2(PostKind.Text, Pillar.Reveal))
        assertFalse(SlotPlanner.requiresSlot2(PostKind.Text, Pillar.Tip))
    }

    @Test
    fun takenSlotsAreSkippedAndAtMostTwoPerDay() {
        val morning = Instant.parse("2026-10-20T05:30:00Z")
        val evening = Instant.parse("2026-10-20T16:30:00Z")
        val result = next("2026-10-20T00:00:00Z", publishedCount = 0, taken = listOf(morning, evening))
        assertEquals(Instant.parse("2026-10-21T05:30:00Z"), result)
    }

    @Test
    fun anOffSlotPostCountsTowardTheDailyCap() {
        val morning = Instant.parse("2026-10-20T05:30:00Z")
        val publishedNow = Instant.parse("2026-10-20T08:00:00Z")
        // Slot 2 is free, but two posts already sit on 10-20.
        val result = next("2026-10-20T00:00:00Z", publishedCount = 0, taken = listOf(morning, publishedNow))
        assertEquals(Instant.parse("2026-10-21T05:30:00Z"), result)
    }

    @Test
    fun aSlotAtOrBeforeNowIsNotFree() {
        assertEquals(
            Instant.parse("2026-10-21T05:30:00Z"),
            next("2026-10-20T05:30:00Z", publishedCount = 10),
        )
    }

    @Test
    fun notAfterBoundsTheSearch() {
        assertNull(
            next("2026-10-20T06:00:00Z", publishedCount = 10, notAfter = Instant.parse("2026-10-21T05:00:00Z")),
        )
        assertEquals(
            Instant.parse("2026-10-21T05:30:00Z"),
            next("2026-10-20T06:00:00Z", publishedCount = 10, notAfter = Instant.parse("2026-10-21T06:00:00Z")),
        )
    }

    @Test
    fun rollAssignsDistinctSlotsInOrder() {
        val now = Instant.parse("2026-10-20T00:00:00Z")
        val rolled = SlotPlanner.roll(
            now = now,
            zone = kyiv,
            publishedCount = 10,
            fixed = listOf(Instant.parse("2026-10-20T05:30:00Z")),
            toRoll = listOf(
                SlotPlanner.Rollable("a", slot2Only = false),
                SlotPlanner.Rollable("poll", slot2Only = true),
                SlotPlanner.Rollable("b", slot2Only = false),
            ),
        )
        assertEquals(Instant.parse("2026-10-21T05:30:00Z"), rolled["a"])
        assertEquals(Instant.parse("2026-10-20T16:30:00Z"), rolled["poll"])
        assertEquals(Instant.parse("2026-10-22T05:30:00Z"), rolled["b"])
    }
}
