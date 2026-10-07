package com.sirelon.sellsnap.features.notifications.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

internal class IosPushNotificationsPlatform : PushNotificationsPlatform {

    override suspend fun notificationsEnabled(): Boolean = when (authorizationStatus()) {
        UNAuthorizationStatusAuthorized,
        UNAuthorizationStatusProvisional,
        UNAuthorizationStatusEphemeral -> true
        else -> false
    }

    // iOS shows its prompt once; after any answer only the Settings app can change it.
    override suspend fun canShowSystemPrompt(): Boolean =
        authorizationStatus() == UNAuthorizationStatusNotDetermined

    override fun subscribeToTopic(topic: String) {
        Firebase.messaging.subscribeToTopic(topic)
    }

    override fun unsubscribeFromTopic(topic: String) {
        Firebase.messaging.unsubscribeFromTopic(topic)
    }

    private suspend fun authorizationStatus(): UNAuthorizationStatus = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            if (continuation.isActive) {
                continuation.resume(settings?.authorizationStatus ?: UNAuthorizationStatusNotDetermined)
            }
        }
    }
}

actual val pushNotificationsPlatformModule: Module = module {
    single<PushNotificationsPlatform> { IosPushNotificationsPlatform() }
}
