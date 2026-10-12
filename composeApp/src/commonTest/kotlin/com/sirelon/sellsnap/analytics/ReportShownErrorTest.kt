package com.sirelon.sellsnap.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReportShownErrorTest {

    @Test
    fun `logs error_shown with the screen and reason codes`() {
        val analytics = RecordingAnalytics()

        analytics.reportShownError(screen = "preview_ad", reason = "validation:district_id")

        assertEquals<List<Pair<String, Map<String, Any>>>>(
            listOf(AnalyticsEvents.ERROR_SHOWN to mapOf("screen" to "preview_ad", "reason" to "validation:district_id")),
            analytics.events,
        )
        assertEquals(listOf("error_shown preview_ad: validation:district_id"), analytics.breadcrumbs)
    }

    @Test
    fun `records the throwable it was given as the non-fatal`() {
        val analytics = RecordingAnalytics()
        val failure = IllegalStateException("boom")

        analytics.reportShownError(screen = "my_adverts", reason = "load_failed:network_failure", throwable = failure)

        assertSame(failure, analytics.exceptions.single())
    }

    @Test
    fun `records a ShownErrorException carrying screen and reason when there is no throwable`() {
        val analytics = RecordingAnalytics()

        analytics.reportShownError(screen = "preview_ad", reason = "account_mismatch")

        val recorded = assertIs<ShownErrorException>(analytics.exceptions.single())
        assertEquals("preview_ad: account_mismatch", recorded.message)
    }

    @Test
    fun `a validation nudge logs the event and breadcrumb but no non-fatal`() {
        val analytics = RecordingAnalytics()

        analytics.reportShownError(screen = "preview_ad", reason = "title_too_short", recordNonFatal = false)

        assertEquals(AnalyticsEvents.ERROR_SHOWN, analytics.events.single().first)
        assertEquals(1, analytics.breadcrumbs.size)
        assertTrue(analytics.exceptions.isEmpty())
    }

    @Test
    fun `caps param values at Firebase's 100-character limit`() {
        val analytics = RecordingAnalytics()

        analytics.reportShownError(screen = "preview_ad", reason = "attributes_invalid:" + "x".repeat(200))

        assertEquals(100, (analytics.events.single().second["reason"] as String).length)
    }

    private class RecordingAnalytics : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val breadcrumbs = mutableListOf<String>()
        val exceptions = mutableListOf<Throwable>()

        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }

        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {}
        override fun recordException(throwable: Throwable, message: String?) {
            exceptions += throwable
        }

        override fun log(message: String) {
            breadcrumbs += message
        }

        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }
}
