package com.sirelon.sellsnap.features.seller.messages.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.format.char
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val WallClockFormat = LocalDateTime.Format {
    date(LocalDate.Formats.ISO)
    char(' ')
    time(LocalTime.Formats.ISO)
}

/**
 * Reads an OLX timestamp such as `2026-10-30 21:33:53` as an instant, or null when it is not in
 * that format.
 *
 * Inferred: OLX returns the market's local time without an offset (SIR-88, olx.ua), so the text
 * is placed on the timeline with [marketZone] (`OlxCountry.timeZone`).
 */
fun parseOlxInstant(raw: String, marketZone: TimeZone): Instant? =
    runCatching { LocalDateTime.parse(raw, WallClockFormat).toInstant(marketZone) }.getOrNull()

/** How long ago something happened, before it is turned into words. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class MinutesAgo(val minutes: Int) : RelativeTime
    data class HoursAgo(val hours: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class DaysAgo(val days: Int) : RelativeTime

    /** A calendar date such as `30.10`, or the raw OLX text when it could not be read. */
    data class Verbatim(val text: String) : RelativeTime
}

/**
 * [raw] relative to [now]. Under a minute is [RelativeTime.JustNow], then minutes, then hours up
 * to a day; beyond that the calendar decides, in [zone] (the device's): the day before is
 * [RelativeTime.Yesterday], up to a week is days, and anything older is the date, with the year
 * only when it is not [now]'s. A time in the future, from clock skew, counts as just now.
 */
fun relativeTime(raw: String, marketZone: TimeZone, now: Instant, zone: TimeZone): RelativeTime {
    val instant = parseOlxInstant(raw, marketZone) ?: return RelativeTime.Verbatim(raw)
    val elapsed = now - instant
    if (elapsed < 1.minutes) return RelativeTime.JustNow
    if (elapsed < 1.hours) return RelativeTime.MinutesAgo(elapsed.inWholeMinutes.toInt())
    if (elapsed < 24.hours) return RelativeTime.HoursAgo(elapsed.inWholeHours.toInt())

    val date = instant.toLocalDateTime(zone).date
    val today = now.toLocalDateTime(zone).date
    return when (val days = date.daysUntil(today)) {
        // A 25-hour day (clocks going back) can hold more than 24 hours.
        0 -> RelativeTime.HoursAgo(elapsed.inWholeHours.toInt())
        1 -> RelativeTime.Yesterday
        in 2..6 -> RelativeTime.DaysAgo(days)
        else -> RelativeTime.Verbatim(
            if (date.year == today.year) date.dayMonth() else "${date.dayMonth()}.${date.year}",
        )
    }
}

/**
 * The time on a message bubble in [zone] (the device's): `21:33` for today, `30.10 21:33` for any
 * other day. [raw] is returned unchanged when it cannot be read.
 */
fun messageTime(raw: String, marketZone: TimeZone, now: Instant, zone: TimeZone): String {
    val instant = parseOlxInstant(raw, marketZone) ?: return raw
    val local = instant.toLocalDateTime(zone)
    val clock = "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
    return if (local.date == now.toLocalDateTime(zone).date) clock else "${local.date.dayMonth()} $clock"
}

private fun LocalDate.dayMonth() = "${day.twoDigits()}.${month.number.twoDigits()}"

private fun Int.twoDigits() = toString().padStart(2, '0')
