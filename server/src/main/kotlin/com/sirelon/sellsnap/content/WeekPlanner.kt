package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The weekly template in code (design §6.1, as Phase 2 builds it). Slot 1 holds a tip every day; Sunday's
 * slot 1 is a product update when a release is due. Slot 2 holds a tip only during the launch cadence, while
 * the projected post count is under [SlotPlanner.LAUNCH_POST_COUNT]; after launch it stays empty. At most
 * [SlotPlanner.MAX_PER_DAY] posts per local day, counting posts that already hold a slot.
 */
object WeekPlanner {
    /** One post to draft: [slot] is 1 or 2, [time] its local time and [scheduledAt] that time in the account's zone. */
    class SlotPlan(
        val date: LocalDate,
        val slot: Int,
        val time: LocalTime,
        val scheduledAt: Instant,
        val pillar: Pillar,
        val kind: PostKind = PostKind.Text,
        /** Only an update's link reply may carry `{link}` (D23). */
        val linkReplyAllowed: Boolean,
    )

    /**
     * Plans the seven days from [weekStart]. [publishedCount] is the account's published posts so far and
     * [occupied] the scheduled instants of every post that holds a slot (`SlotPlanner.SLOT_HOLDERS`);
     * those after [now] count as already scheduled in the launch projection. A template slot that is
     * occupied is skipped. [releaseDue] makes Sunday's slot 1 an update.
     */
    fun plan(
        zone: ZoneId,
        weekStart: LocalDate,
        now: Instant,
        publishedCount: Int,
        occupied: Collection<Instant>,
        releaseDue: Boolean,
    ): List<SlotPlan> {
        val occupiedSet = occupied.toSet()
        val perDay = occupied.groupingBy { it.atZone(zone).toLocalDate() }.eachCount().toMutableMap()
        var projected = publishedCount + occupied.count { it > now }
        val plans = mutableListOf<SlotPlan>()

        for (offset in 0 until DAYS) {
            val date = weekStart.plusDays(offset.toLong())
            for ((index, time) in listOf(SlotPlanner.SLOT_1, SlotPlanner.SLOT_2).withIndex()) {
                val slot = index + 1
                if (slot == 2 && projected >= SlotPlanner.LAUNCH_POST_COUNT) continue
                if ((perDay[date] ?: 0) >= SlotPlanner.MAX_PER_DAY) continue
                val scheduledAt = date.atTime(time).atZone(zone).toInstant()
                if (scheduledAt in occupiedSet) continue

                val pillar = if (slot == 1 && date.dayOfWeek == DayOfWeek.SUNDAY && releaseDue) Pillar.Update else Pillar.Tip
                plans += SlotPlan(date, slot, time, scheduledAt, pillar, PostKind.Text, pillar in ContentRules.LINK_PILLARS)
                perDay[date] = (perDay[date] ?: 0) + 1
                projected++
            }
        }
        return plans
    }

    private const val DAYS = 7
}
