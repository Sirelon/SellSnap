package com.sirelon.sellsnap.batch

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The batch calendar: which Monday a run drafts for and the key of that ISO week. All dates are Kyiv dates. */
object IsoWeek {
    val KYIV: ZoneId = ZoneId.of("Europe/Kyiv")

    /**
     * The Monday a batch run on [today] drafts for: the next Monday, or the current one on any day after
     * Sunday. A Sunday run drafts the coming week; a retry on Monday or later in the week targets the same week.
     */
    fun targetMonday(today: LocalDate): LocalDate = today.plusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun targetMonday(now: Instant): LocalDate = targetMonday(now.atZone(KYIV).toLocalDate())

    /** `2026-W43`: the ISO week-based year and the zero-padded ISO week number of [date]. */
    fun key(date: LocalDate): String {
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return String.format(Locale.ROOT, "%04d-W%02d", year, week)
    }

    fun key(now: Instant): String = key(now.atZone(KYIV).toLocalDate())
}
