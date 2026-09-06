package com.sirelon.sellsnap.features.review

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.datastore.KeyValueStore
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingAnalytics : Analytics {
    val events = mutableListOf<Pair<String, Map<String, Any>>>()
    override fun logEvent(name: String, params: Map<String, Any>) {
        events += name to params
    }

    override fun setUserId(userId: String?) {}
    override fun setUserProperty(name: String, value: String?) {}
    override fun recordException(throwable: Throwable, message: String?) {}
    override fun log(message: String) {}
    override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
    override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
}

private class ExplodingKeyValueStore : KeyValueStore {
    override suspend fun getString(key: String): String? = throw IllegalStateException("corrupt")
    override suspend fun putString(key: String, value: String) = throw IllegalStateException("corrupt")
    override suspend fun remove(key: String) = throw IllegalStateException("corrupt")
}

private fun coordinator(
    storage: KeyValueStore = InMemoryOlxKeyValueStore(),
    analytics: Analytics = RecordingAnalytics(),
) = ReviewPromptCoordinator(ReviewPromptStore(storage), analytics)

class ReviewPromptCoordinatorTest {

    @Test
    fun `an eligible publish records the cooldown before saying yes`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        val analytics = RecordingAnalytics()
        val subject = coordinator(storage, analytics).apply { isReturningSession = true }
        val store = ReviewPromptStore(storage)
        store.incrementPublishCount()
        store.incrementPublishCount()

        assertTrue(subject.requestIfEligible())

        // Written before the platform call returns, because the platform call reports nothing: an
        // unwritten timestamp would let the next publish spend another of Apple's three slots.
        assertTrue(store.lastPromptEpochSeconds() != null)
        val requested = analytics.events.single { it.first == AnalyticsEvents.REVIEW_PROMPT_REQUESTED }
        assertEquals(2, requested.second["publish_count"])
        assertEquals(true, requested.second["returning_session"])
    }

    @Test
    fun `a skipped publish reports why and leaves the cooldown untouched`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        val analytics = RecordingAnalytics()
        val subject = coordinator(storage, analytics).apply { isReturningSession = true }

        assertFalse(subject.requestIfEligible())

        val skipped = analytics.events.single { it.first == AnalyticsEvents.REVIEW_PROMPT_SKIPPED }
        assertEquals(
            ReviewPromptSkipReason.TooFewPublishes.analyticsValue,
            skipped.second["reason"],
        )
        assertNull(ReviewPromptStore(storage).lastPromptEpochSeconds())
    }

    @Test
    fun `onPublishSucceeded counts and onPublishFailed poisons the session`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        val subject = coordinator(storage).apply { isReturningSession = true }

        subject.onPublishSucceeded()
        subject.onPublishSucceeded()
        assertEquals(2, ReviewPromptStore(storage).publishCount())

        subject.onPublishFailed()
        assertFalse(subject.requestIfEligible())
    }

    @Test
    fun `unreadable storage skips instead of crashing the success screen`() = runTest {
        val analytics = RecordingAnalytics()
        val subject = coordinator(ExplodingKeyValueStore(), analytics).apply { isReturningSession = true }

        // Runs inside a LaunchedEffect: an escaping exception here would take the process down two
        // seconds after telling the seller their advert is live.
        assertFalse(subject.requestIfEligible())
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `the debug bypass asks without spending the cooldown`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        val analytics = RecordingAnalytics()
        val subject = coordinator(storage, analytics)
        reviewPromptAlwaysRequest = true
        try {
            assertTrue(subject.requestIfEligible())
        } finally {
            reviewPromptAlwaysRequest = false
        }

        // Otherwise the first local test would lock the flag out for 130 days.
        assertNull(ReviewPromptStore(storage).lastPromptEpochSeconds())
        assertTrue(analytics.events.isEmpty())
    }
}
