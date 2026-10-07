package com.sirelon.sellsnap.features.notifications.data

import com.sirelon.sellsnap.analytics.Analytics
import com.sirelon.sellsnap.features.notifications.pushLanguageTopicFor
import com.sirelon.sellsnap.features.notifications.pushTopicsFor
import com.sirelon.sellsnap.platform.getDeviceLanguageCode
import kotlinx.coroutines.CancellationException

class PushNotificationsRepository(
    private val platform: PushNotificationsPlatform,
    private val store: NotificationsStore,
    private val analytics: Analytics,
    private val deviceLanguage: () -> String? = ::getDeviceLanguageCode,
) {

    private var lastReported: Boolean? = null

    /** Reports the permission and brings the topic subscriptions in line with the device language. */
    suspend fun onAppLaunch() {
        reportPermission()
        syncTopics()
    }

    /**
     * Sets the `notifications_enabled` user property. The analytics SDK drops it while consent is
     * off, so consent is enforced by the SDK switch, not here.
     */
    suspend fun reportPermission() {
        report(safely(false) { platform.notificationsEnabled() })
    }

    /**
     * Reads the current permission and reports it only when it differs from what was last
     * reported this process. Returns the current value.
     */
    suspend fun refreshPermission(): Boolean {
        val enabled = safely(false) { platform.notificationsEnabled() }
        if (enabled != lastReported) report(enabled)
        return enabled
    }

    /** True while the OS can still show its permission prompt: see [PushNotificationsPlatform.canShowSystemPrompt]. */
    suspend fun canShowSystemPrompt(): Boolean = safely(false) { platform.canShowSystemPrompt() }

    suspend fun wasPermissionRequested(): Boolean = safely(false) { store.wasPermissionRequested() }

    /** Records that the OS permission prompt was launched; Android only re-prompts after a rationale. */
    suspend fun markPermissionRequested() {
        safely(Unit) { store.markPermissionRequested() }
    }

    /** True while the one-time prompt may still be offered: never shown, off, and the OS can ask. */
    suspend fun shouldOfferPrompt(): Boolean =
        !store.isPromptShown() &&
            !safely(false) { platform.notificationsEnabled() } &&
            safely(false) { platform.canShowSystemPrompt() }

    suspend fun markPromptShown() {
        store.markPromptShown()
    }

    private fun report(enabled: Boolean) {
        lastReported = enabled
        analytics.setUserProperty(PROPERTY_NOTIFICATIONS_ENABLED, enabled.toString())
    }

    // Subscribed every launch rather than once: the SDK queues a subscription until a token
    // exists, and a failed first attempt would otherwise never be retried.
    private suspend fun syncTopics() {
        val language = deviceLanguage()
        val previous = safely(null) { store.languageTopic() }
        val current = pushLanguageTopicFor(language)
        if (previous != null && previous != current) {
            safely(Unit) { platform.unsubscribeFromTopic(previous) }
        }
        pushTopicsFor(language).forEach { topic ->
            safely(Unit) { platform.subscribeToTopic(topic) }
        }
        safely(Unit) { store.setLanguageTopic(current) }
    }

    private inline fun <T> safely(fallback: T, block: () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        error.printStackTrace()
        fallback
    }

    private companion object {
        const val PROPERTY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    }
}
