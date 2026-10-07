package com.sirelon.sellsnap.features.seller.settings.presentation

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import com.sirelon.sellsnap.features.notifications.data.NotificationsStore
import com.sirelon.sellsnap.features.notifications.data.PushNotificationsPlatform
import com.sirelon.sellsnap.features.notifications.data.PushNotificationsRepository
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsEffect
import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsContract.SettingsEvent
import com.sirelon.sellsnap.startup.AnalyticsConsentRepository
import com.sirelon.sellsnap.startup.AnalyticsConsentStore
import com.sirelon.sellsnap.startup.AppThemePreferenceStore
import com.sirelon.sellsnap.startup.AppThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {

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
    fun `notifications state is unknown until the first refresh`() = runTest(testDispatcher) {
        val harness = harness(enabled = true)

        assertNull(harness.viewModel.state.value.notificationsEnabled)

        harness.refresh()

        assertEquals<Boolean?>(true, harness.viewModel.state.value.notificationsEnabled)
    }

    @Test
    fun `refresh reports the user property only when the value changed`() = runTest(testDispatcher) {
        val harness = harness(enabled = false)

        harness.refresh()
        harness.refresh()
        assertEquals(listOf("notifications_enabled" to "false"), harness.analytics.properties)

        harness.platform.enabled = true
        harness.refresh()
        harness.refresh()

        assertEquals<Boolean?>(true, harness.viewModel.state.value.notificationsEnabled)
        assertEquals(
            listOf("notifications_enabled" to "false", "notifications_enabled" to "true"),
            harness.analytics.properties,
        )
    }

    @Test
    fun `refresh does not report again after a cold launch report`() = runTest(testDispatcher) {
        val harness = harness(enabled = true)
        harness.repository.onAppLaunch()

        harness.refresh()

        assertEquals(listOf("notifications_enabled" to "true"), harness.analytics.properties)
    }

    @Test
    fun `tapping while enabled opens the app settings`() = runTest(testDispatcher) {
        val harness = harness(enabled = true)
        harness.refresh()

        assertEquals(SettingsEffect.OpenAppSettings, harness.tap())
    }

    @Test
    fun `tapping while off on iOS asks the OS while it can`() = runTest(testDispatcher) {
        val harness = harness(enabled = false, isAndroid = false)
        harness.refresh()

        assertEquals(SettingsEffect.RequestNotificationPermission, harness.tap())

        harness.platform.canPrompt = false
        assertEquals(SettingsEffect.OpenAppSettings, harness.tap())
    }

    @Test
    fun `tapping while off on Android asks once, then only after a rationale`() = runTest(testDispatcher) {
        val harness = harness(enabled = false, isAndroid = true)
        harness.refresh()

        assertEquals(SettingsEffect.RequestNotificationPermission, harness.tap())
        assertTrue(harness.repository.wasPermissionRequested())

        assertEquals(SettingsEffect.OpenAppSettings, harness.tap(shouldShowRationale = false))
        assertEquals(SettingsEffect.RequestNotificationPermission, harness.tap(shouldShowRationale = true))
    }

    @Test
    fun `a request launched elsewhere counts as requested`() = runTest(testDispatcher) {
        val harness = harness(enabled = false, isAndroid = true)
        harness.refresh()
        harness.repository.markPermissionRequested()

        assertEquals(SettingsEffect.OpenAppSettings, harness.tap())
    }

    @Test
    fun `tapping while off on Android below 13 opens the app settings`() = runTest(testDispatcher) {
        val harness = harness(enabled = false, canPrompt = false, isAndroid = true)
        harness.refresh()

        assertEquals(SettingsEffect.OpenAppSettings, harness.tap())
        assertFalse(harness.repository.wasPermissionRequested())
    }

    private inner class Harness(
        val viewModel: SettingsViewModel,
        val repository: PushNotificationsRepository,
        val platform: FakePlatform,
        val analytics: RecordingAnalytics,
    ) {
        suspend fun refresh() {
            viewModel.onEvent(SettingsEvent.RefreshNotifications)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        suspend fun tap(shouldShowRationale: Boolean = false): SettingsEffect {
            viewModel.onEvent(SettingsEvent.NotificationsClicked(shouldShowRationale))
            testDispatcher.scheduler.advanceUntilIdle()
            return viewModel.effects.first()
        }
    }

    private fun harness(
        enabled: Boolean,
        canPrompt: Boolean = true,
        isAndroid: Boolean = true,
    ): Harness {
        val analytics = RecordingAnalytics()
        val platform = FakePlatform(enabled = enabled, canPrompt = canPrompt)
        val repository = PushNotificationsRepository(
            platform = platform,
            store = NotificationsStore(InMemoryOlxKeyValueStore()),
            analytics = analytics,
            deviceLanguage = { "uk" },
        )
        val applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)
        return Harness(
            viewModel = SettingsViewModel(
                themeRepository = AppThemeRepository(
                    store = AppThemePreferenceStore(InMemoryOlxKeyValueStore()),
                    applicationScope = applicationScope,
                ),
                analyticsConsentRepository = AnalyticsConsentRepository(
                    store = AnalyticsConsentStore(InMemoryOlxKeyValueStore()),
                    analytics = analytics,
                    applicationScope = applicationScope,
                ),
                notificationsRepository = repository,
                isAndroid = isAndroid,
            ),
            repository = repository,
            platform = platform,
            analytics = analytics,
        )
    }

    private class FakePlatform(
        var enabled: Boolean,
        var canPrompt: Boolean,
    ) : PushNotificationsPlatform {
        override suspend fun notificationsEnabled(): Boolean = enabled
        override suspend fun canShowSystemPrompt(): Boolean = canPrompt
        override suspend fun subscribeToTopic(topic: String) {}
        override suspend fun unsubscribeFromTopic(topic: String) {}
    }

    private class RecordingAnalytics : Analytics {
        val properties = mutableListOf<Pair<String, String>>()
        override fun logEvent(name: String, params: Map<String, Any>) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String?) {
            properties += name to value.orEmpty()
        }

        override fun recordException(throwable: Throwable, message: String?) {}
        override fun log(message: String) {}
        override fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
        override fun setCrashlyticsCollectionEnabled(enabled: Boolean) {}
    }
}
