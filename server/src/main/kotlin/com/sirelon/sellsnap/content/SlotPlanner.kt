package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostStatus
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Two daily slots in the account's time zone (D13). Polls and reveals take slot 2; other posts
 * take slot 1, and also slot 2 until the account has [LAUNCH_POST_COUNT] published posts.
 * At most [MAX_PER_DAY] posts per local day, whether or not they sit on a slot.
 */
object SlotPlanner {
    val SLOT_1: LocalTime = LocalTime.of(8, 30)
    val SLOT_2: LocalTime = LocalTime.of(19, 30)
    const val LAUNCH_POST_COUNT = 10
    const val MAX_PER_DAY = 2
    private const val SEARCH_DAYS = 14L

    /** Statuses whose `scheduledAt` uses up a slot or counts toward a day's cap. */
    val SLOT_HOLDERS: Set<PostStatus> =
        setOf(PostStatus.Pending, PostStatus.Approved, PostStatus.Publishing, PostStatus.Published)

    class Rollable(val id: String, val slot2Only: Boolean)

    fun requiresSlot2(kind: PostKind, pillar: Pillar): Boolean = kind == PostKind.Poll || pillar == Pillar.Reveal

    /**
     * First free slot strictly after [now], or null when none exists within two weeks or by [notAfter].
     * [taken] holds the scheduled instants of every post that occupies a slot or a day.
     */
    fun nextFreeSlot(
        now: Instant,
        zone: ZoneId,
        publishedCount: Int,
        slot2Only: Boolean,
        taken: Collection<Instant>,
        notAfter: Instant? = null,
    ): Instant? {
        val slots = when {
            slot2Only -> listOf(SLOT_2)
            publishedCount < LAUNCH_POST_COUNT -> listOf(SLOT_1, SLOT_2)
            else -> listOf(SLOT_1)
        }
        val takenSet = taken.toSet()
        val perDay = taken.groupingBy { it.atZone(zone).toLocalDate() }.eachCount()
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0 until SEARCH_DAYS) {
            val date = today.plusDays(offset)
            if ((perDay[date] ?: 0) >= MAX_PER_DAY) continue
            for (time in slots) {
                val slot = date.atTime(time).atZone(zone).toInstant()
                if (slot <= now || slot in takenSet) continue
                return slot.takeUnless { notAfter != null && it > notAfter }
            }
        }
        return null
    }

    /** Gives each of [toRoll], in order, the next free slot; [fixed] are the slots of posts that stay put. */
    fun roll(
        now: Instant,
        zone: ZoneId,
        publishedCount: Int,
        fixed: Collection<Instant>,
        toRoll: List<Rollable>,
    ): Map<String, Instant> {
        val taken = fixed.toMutableList()
        val result = linkedMapOf<String, Instant>()
        for (item in toRoll) {
            val slot = nextFreeSlot(now, zone, publishedCount, item.slot2Only, taken) ?: continue
            taken += slot
            result[item.id] = slot
        }
        return result
    }
}
