package com.sirelon.sellsnap.fakes

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(
    var now: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    fun advance(duration: Duration) {
        now += duration
    }

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}
