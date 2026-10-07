package com.sirelon.sellsnap.features.notifications.presentation

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.analytics.AnalyticsEvents
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.notifications.data.NotificationsStore
import com.sirelon.sellsnap.features.notifications.data.PushNotificationsPlatform
import com.sirelon.sellsnap.features.notifications.data.PushNotificationsRepository
import com.sirelon.sellsnap.features.review.ReviewPromptCoordinator
import com.sirelon.sellsnap.features.review.ReviewPromptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsPromptViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the first session never shows the prompt and marks nothing`() = runTest(testDispatcher) {
        val harness = harness(returning = false)

        assertFalse(harness.viewModel.shouldShowPrompt())

        assertFalse(harness.coordinator.notificationPromptShownThisSession)
        assertTrue(harness.repository.shouldOfferPrompt())
    }

    @Test
    fun `a returning session that can be asked shows the prompt once and marks it shown`() = runTest(testDispatcher) {
        val harness = harness(returning = true)

        assertTrue(harness.viewModel.shouldShowPrompt())

        assertTrue(harness.coordinator.notificationPromptShownThisSession)
        assertTrue(harness.coordinator.launchPromptShownThisSession)
        assertFalse(harness.repository.shouldOfferPrompt())
        assertFalse(harness.viewModel.shouldShowPrompt())
    }

    @Test
    fun `a returning session that cannot be asked shows nothing`() = runTest(testDispatcher) {
        val harness = harness(returning = true, enabled = true)

        assertFalse(harness.viewModel.shouldShowPrompt())
        assertFalse(harness.coordinator.notificationPromptShownThisSession)

        val blocked = harness(returning = true, canPrompt = false)
        assertFalse(blocked.viewModel.shouldShowPrompt())
        assertFalse(blocked.coordinator.notificationPromptShownThisSession)
    }

    @Test
    fun `enabling logs the choice and whether the OS granted it`() = runTest(testDispatcher) {
        val harness = harness(returning = true)

        harness.viewModel.onAnswered(enabled = true, granted = true)
        advanceUntilIdle()

        val event = harness.analytics.events.single { it.first == AnalyticsEvents.NOTIFICATION_PROMPT_ANSWERED }
        assertEquals(mapOf<String, Any>("choice" to "enable", "granted" to true), event.second)
        assertEquals(listOf("notifications_enabled"), harness.analytics.properties.map { it.first })
    }

    @Test
    fun `not now logs the choice without a granted flag`() = runTest(testDispatcher) {
        val harness = harness(returning = true)

        harness.viewModel.onAnswered(enabled = false, granted = false)
        advanceUntilIdle()

        val event = harness.analytics.events.single { it.first == AnalyticsEvents.NOTIFICATION_PROMPT_ANSWERED }
        assertEquals(mapOf<String, Any>("choice" to "not_now"), event.second)
    }

    @Test
    fun `only the first answer of a showing is logged`() = runTest(testDispatcher) {
        val harness = harness(returning = true)

        harness.viewModel.onAnswered(enabled = true, granted = false)
        harness.viewModel.onAnswered(enabled = false, granted = false)
        advanceUntilIdle()

        val events = harness.analytics.events.filter { it.first == AnalyticsEvents.NOTIFICATION_PROMPT_ANSWERED }
        assertEquals(listOf("enable"), events.map { it.second["choice"] })
    }

    private class Harness(
        val viewModel: NotificationsPromptViewModel,
        val repository: PushNotificationsRepository,
        val coordinator: ReviewPromptCoordinator,
        val analytics: RecordingAnalytics,
    )

    private fun harness(
        returning: Boolean,
        enabled: Boolean = false,
        canPrompt: Boolean = true,
    ): Harness {
        val analytics = RecordingAnalytics()
        val repository = PushNotificationsRepository(
            platform = FakePlatform(enabled = enabled, canPrompt = canPrompt),
            store = NotificationsStore(InMemoryOlxKeyValueStore()),
            analytics = analytics,
            deviceLanguage = { "uk" },
        )
        val coordinator = ReviewPromptCoordinator(
            store = ReviewPromptStore(InMemoryOlxKeyValueStore()),
            analytics = analytics,
        ).apply { isReturningSession = returning }
        return Harness(
            viewModel = NotificationsPromptViewModel(
                repository = repository,
                reviewPromptCoordinator = coordinator,
                analytics = analytics,
            ),
            repository = repository,
            coordinator = coordinator,
            analytics = analytics,
        )
    }

    private class FakePlatform(
        private val enabled: Boolean,
        private val canPrompt: Boolean,
    ) : PushNotificationsPlatform {
        override suspend fun notificationsEnabled(): Boolean = enabled
        override suspend fun canShowSystemPrompt(): Boolean = canPrompt
        override fun subscribeToTopic(topic: String) {}
        override fun unsubscribeFromTopic(topic: String) {}
    }

    private class RecordingAnalytics : Analytics {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        val properties = mutableListOf<Pair<String, String?>>()
        override fun logEvent(name: String, params: Map<String, Any>) {
            events += name to params
        }

        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {
            properties += name to value
        }

        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }
}
