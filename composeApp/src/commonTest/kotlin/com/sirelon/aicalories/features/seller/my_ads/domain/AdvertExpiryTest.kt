package com.sirelon.sellsnap.features.seller.my_ads.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Regression tests for SIR-105's expiry math. `now` is always passed explicitly rather than read
 * from the real clock, and `valid_to` values use OLX's real ISO-8601-with-offset timestamp format.
 */
class AdvertExpiryTest {

    private val now = Instant.parse("2026-04-28T11:00:00+02:00")

    @Test
    fun `advertExpiryOf reports the correct days left for a future valid_to`() {
        val expiry = advertExpiryOf("2026-05-03T11:00:00+02:00", now)

        val remaining = assertIs<AdvertExpiry.Remaining>(expiry)
        assertEquals(5, remaining.daysLeft)
    }

    @Test
    fun `advertExpiryOf rounds 25 hours left down to 1 day rather than 0`() {
        val expiry = advertExpiryOf("2026-04-29T12:00:00+02:00", now)

        val remaining = assertIs<AdvertExpiry.Remaining>(expiry)
        assertEquals(1, remaining.daysLeft)
    }

    @Test
    fun `advertExpiryOf reports 0 days left for 3 hours out, so it reads as expires today`() {
        val expiry = advertExpiryOf("2026-04-28T14:00:00+02:00", now)

        val remaining = assertIs<AdvertExpiry.Remaining>(expiry)
        assertEquals(0, remaining.daysLeft)
    }

    @Test
    fun `advertExpiryOf reports Expired for a valid_to already in the past`() {
        assertEquals(AdvertExpiry.Expired, advertExpiryOf("2026-04-28T10:00:00+02:00", now))
    }

    @Test
    fun `advertExpiryOf reports Unknown for blank or unparseable valid_to rather than guessing`() {
        // Showing a wrong expiry date is worse than showing none.
        assertEquals(AdvertExpiry.Unknown, advertExpiryOf("", now))
        assertEquals(AdvertExpiry.Unknown, advertExpiryOf("not-a-date", now))
    }

    @Test
    fun `isExpiringSoon is true at and below the threshold and false just above it`() {
        assertTrue(AdvertExpiry.Remaining(daysLeft = 0).isExpiringSoon)
        assertTrue(AdvertExpiry.Remaining(daysLeft = ExpiringSoonDays).isExpiringSoon)
        assertFalse(AdvertExpiry.Remaining(daysLeft = ExpiringSoonDays + 1).isExpiringSoon)
    }

    @Test
    fun `isExpiringSoon is false for Expired and Unknown, since neither has a days-left figure to compare`() {
        assertFalse(AdvertExpiry.Expired.isExpiringSoon)
        assertFalse(AdvertExpiry.Unknown.isExpiringSoon)
    }
}
