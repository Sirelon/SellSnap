package com.sirelon.sellsnap.features.seller.auth.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SIR-123: on Android a closed OLX login is inferred from resuming without a new callback. */
class OlxAuthReturnTrackerTest {

    private var published = 0
    private val tracker = OlxAuthReturnTracker { published }

    @Test
    fun `resuming without a launch is not a dismissal`() {
        // The resume effect also runs on first composition, before any login was launched.
        assertFalse(tracker.onResumed())
    }

    @Test
    fun `coming back with no callback is a dismissal, reported once`() {
        tracker.onLaunched()

        assertTrue(tracker.onResumed())
        assertFalse(tracker.onResumed(), "a later resume belongs to no login")
    }

    @Test
    fun `coming back with the login's callback is not a dismissal`() {
        tracker.onLaunched()
        published++ // MainActivity.onNewIntent publishes the redirect before onResume

        assertFalse(tracker.onResumed())
    }

    @Test
    fun `callbacks published before the launch do not count`() {
        published = 3
        tracker.onLaunched()

        assertTrue(tracker.onResumed())
    }
}
