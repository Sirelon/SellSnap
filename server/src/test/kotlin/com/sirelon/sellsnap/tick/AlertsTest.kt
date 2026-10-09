package com.sirelon.sellsnap.tick

import com.sirelon.sellsnap.AgentFixture
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.threads.AlertRequest
import com.sirelon.sellsnap.threads.AlertType
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertsTest {
    private val fx = AgentFixture()
    private val alerts = Alerts(fx.store, fx.telegram, fx.clock, AgentFixture.OWNER)
    private val failed = AlertRequest(AlertType.PublishFailed, "staging", postId = "stt0001", detail = "Threads rejected the request")

    @Test
    fun theSameAlertIsSentOncePerDay() = runTest {
        assertTrue(alerts.sendOnce(failed))
        assertFalse(alerts.sendOnce(failed))
        assertFalse(alerts.sendOnce(failed.copy(detail = "another wording")))
        assertEquals(1, fx.sentTexts().size)
        assertEquals(setOf("alert_publish_failed_staging_20261012"), fx.memory.events.keys)
        assertEquals(EventType.Alert, fx.memory.events.values.single().type)

        fx.clock.advance(Duration.ofDays(1))
        assertTrue(alerts.sendOnce(failed))
        assertEquals(2, fx.sentTexts().size)
    }

    @Test
    fun otherTypesAndAccountsAreNotDeduplicatedAgainstEachOther() = runTest {
        assertTrue(alerts.sendOnce(failed))
        assertTrue(alerts.sendOnce(failed.copy(accountId = "pl")))
        assertTrue(alerts.sendOnce(AlertRequest(AlertType.PublishStalled, "staging", postId = "stt0001")))
        assertEquals(3, fx.sentTexts().size)
    }

    @Test
    fun sendSkipsTheDedupCheckForAlertsAlreadyRecorded() = runTest {
        alerts.sendOnce(failed)
        alerts.send(failed)
        assertEquals(2, fx.sentTexts().size)
    }

    @Test
    fun everyAlertTypeRendersOneShortMessageNamingTheAccount() {
        val expires = Instant.parse("2026-11-01T00:00:00Z")
        for (type in AlertType.entries) {
            val text = Alerts.render(AlertRequest(type, "staging", postId = "stt0001", detail = "boom", expiresAt = expires))
            assertTrue("staging" in text, type.name)
            assertFalse('\n' in text, type.name)
            assertTrue(text.length < 250, type.name)
        }
        assertTrue("1 Nov 2026" in Alerts.render(AlertRequest(AlertType.TokenExpiry, "staging", expiresAt = expires)))
    }

    @Test
    fun theDetailIsRedactedAndCapped() {
        val text = Alerts.render(
            AlertRequest(AlertType.TickFailed, "staging", detail = "GET ?access_token=SECRET123 failed " + "x".repeat(500)),
        )
        assertFalse("SECRET123" in text)
        assertTrue(text.length < 350)
    }
}
