package com.sirelon.sellsnap.features.notifications.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

/**
 * Swift (`iOSApp.swift`) calls [onTokenReady] from `MessagingDelegate` once FCM has its
 * registration token, which it reports on every launch. Before that, FCM refuses a topic operation
 * (error 505, "No APNS token specified") and does not retry it, so topic calls wait for this signal.
 */
object PushTokenBridge {
    internal val tokenReady = MutableStateFlow(false)

    fun onTokenReady() {
        tokenReady.value = true
    }
}

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

    override suspend fun subscribeToTopic(topic: String) {
        PushTokenBridge.tokenReady.first { it }
        Firebase.messaging.subscribeToTopic(topic)
    }

    override suspend fun unsubscribeFromTopic(topic: String) {
        PushTokenBridge.tokenReady.first { it }
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
