package com.sirelon.sellsnap.features.seller.my_ads.domain

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * How much of a listing's validity is left (SIR-105). An advert that quietly lapses is a listing
 * the seller believes is live while no one can see it, so this is derived for every advert from
 * the `valid_to` the list call already returns - no extra call, no extra permission.
 */
sealed interface AdvertExpiry {
    /** `valid_to` was absent or not a date this app could read. Nothing is shown. */
    data object Unknown : AdvertExpiry

    /** [daysLeft] is 0 on the final day, so "expires today" is distinguishable from "1 day left". */
    data class Remaining(val daysLeft: Int) : AdvertExpiry

    data object Expired : AdvertExpiry
}

/** At or below this many days left, the listing is called out rather than merely dated. */
const val ExpiringSoonDays = 3

val AdvertExpiry.isExpiringSoon: Boolean
    get() = this is AdvertExpiry.Remaining && daysLeft <= ExpiringSoonDays

/**
 * Reads OLX's `valid_to` (an ISO-8601 timestamp with offset, e.g. `2026-04-28T11:00:00+02:00`).
 * An unparseable or blank value yields [AdvertExpiry.Unknown] rather than a guess: showing a
 * wrong expiry date is worse than showing none.
 *
 * Days are counted by rounding the remaining duration down, so 25 hours left reads as "1 day",
 * and anything under a day reads as expiring today.
 */
fun advertExpiryOf(validTo: String, now: Instant = Clock.System.now()): AdvertExpiry {
    val validToInstant = runCatching { Instant.parse(validTo) }.getOrNull() ?: return AdvertExpiry.Unknown
    val remaining = validToInstant - now
    if (remaining.isNegative()) return AdvertExpiry.Expired
    return AdvertExpiry.Remaining(daysLeft = remaining.inWholeDays.toInt())
}
