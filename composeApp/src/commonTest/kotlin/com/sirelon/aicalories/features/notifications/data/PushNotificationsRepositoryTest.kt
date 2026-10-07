package com.sirelon.sellsnap.features.notifications.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.auth.data.InMemoryOlxKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushNotificationsRepositoryTest {

    @Test
    fun `reports the permission as a user property`() = runTest {
        val enabled = setup(enabled = true)
        enabled.repository.onAppLaunch()
        assertEquals(listOf("notifications_enabled" to "true"), enabled.analytics.properties)

        val disabled = setup(enabled = false)
        disabled.repository.onAppLaunch()
        assertEquals(listOf("notifications_enabled" to "false"), disabled.analytics.properties)
    }

    @Test
    fun `first launch subscribes to everyone and the language topic`() = runTest {
        val setup = setup(language = "uk")

        setup.repository.onAppLaunch()

        assertEquals(listOf("all", "all-uk"), setup.platform.subscribed)
        assertTrue(setup.platform.unsubscribed.isEmpty())
    }

    @Test
    fun `a language change leaves the old language topic`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        val first = setup(language = "uk", storage = storage)
        first.repository.onAppLaunch()

        val second = setup(language = "pl", storage = storage)
        second.repository.onAppLaunch()

        assertEquals(listOf("all-uk"), second.platform.unsubscribed)
        assertEquals(listOf("all", "all-pl"), second.platform.subscribed)
    }

    @Test
    fun `the same language never unsubscribes and subscribes again`() = runTest {
        val storage = InMemoryOlxKeyValueStore()
        setup(language = "uk", storage = storage).repository.onAppLaunch()

        val again = setup(language = "uk", storage = storage)
        again.repository.onAppLaunch()

        assertTrue(again.platform.unsubscribed.isEmpty())
        assertEquals(listOf("all", "all-uk"), again.platform.subscribed)
    }

    @Test
    fun `the prompt is offered when it was never shown, notifications are off and the OS can ask`() = runTest {
        assertTrue(setup(enabled = false, canPrompt = true).repository.shouldOfferPrompt())
    }

    @Test
    fun `the prompt is not offered once shown`() = runTest {
        val setup = setup(enabled = false, canPrompt = true)
        setup.repository.markPromptShown()
        assertFalse(setup.repository.shouldOfferPrompt())
    }

    @Test
    fun `the prompt is not offered when notifications are already on`() = runTest {
        assertFalse(setup(enabled = true, canPrompt = true).repository.shouldOfferPrompt())
    }

    @Test
    fun `the prompt is not offered when the OS cannot ask`() = runTest {
        assertFalse(setup(enabled = false, canPrompt = false).repository.shouldOfferPrompt())
    }

    @Test
    fun `refresh reports on the first read and then only when the value changed`() = runTest {
        val setup = setup(enabled = true)

        assertTrue(setup.repository.refreshPermission())
        assertTrue(setup.repository.refreshPermission())
        assertEquals(listOf("notifications_enabled" to "true"), setup.analytics.properties)
    }

    @Test
    fun `the permission request is remembered`() = runTest {
        val setup = setup()
        assertFalse(setup.repository.wasPermissionRequested())

        setup.repository.markPermissionRequested()

        assertTrue(setup.repository.wasPermissionRequested())
    }

    @Test
    fun `platform failures do not escape`() = runTest {
        val setup = setup(fail = true)

        setup.repository.onAppLaunch()

        assertFalse(setup.repository.shouldOfferPrompt())
        assertEquals(listOf("notifications_enabled" to "false"), setup.analytics.properties)
    }

    private class Setup(
        val repository: PushNotificationsRepository,
        val platform: FakePlatform,
        val analytics: FakeAnalytics,
    )

    private fun setup(
        enabled: Boolean = false,
        canPrompt: Boolean = true,
        language: String? = "uk",
        fail: Boolean = false,
        storage: InMemoryOlxKeyValueStore = InMemoryOlxKeyValueStore(),
    ): Setup {
        val platform = FakePlatform(enabled = enabled, canPrompt = canPrompt, fail = fail)
        val analytics = FakeAnalytics()
        return Setup(
            repository = PushNotificationsRepository(
                platform = platform,
                store = NotificationsStore(storage),
                analytics = analytics,
                deviceLanguage = { language },
            ),
            platform = platform,
            analytics = analytics,
        )
    }

    private class FakePlatform(
        private val enabled: Boolean,
        private val canPrompt: Boolean,
        private val fail: Boolean,
    ) : PushNotificationsPlatform {
        val subscribed = mutableListOf<String>()
        val unsubscribed = mutableListOf<String>()

        override suspend fun notificationsEnabled(): Boolean = if (fail) error("boom") else enabled

        override suspend fun canShowSystemPrompt(): Boolean = if (fail) error("boom") else canPrompt

        override suspend fun subscribeToTopic(topic: String) {
            if (fail) error("boom")
            subscribed += topic
        }

        override suspend fun unsubscribeFromTopic(topic: String) {
            if (fail) error("boom")
            unsubscribed += topic
        }
    }

    private class FakeAnalytics : Analytics {
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
