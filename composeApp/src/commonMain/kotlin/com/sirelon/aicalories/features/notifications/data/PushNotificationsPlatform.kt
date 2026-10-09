package com.sirelon.sellsnap.features.notifications.data

import org.koin.core.module.Module

interface PushNotificationsPlatform {
    /** Whether the OS currently lets this app post notifications. */
    suspend fun notificationsEnabled(): Boolean

    /**
     * False when the OS will not show its permission prompt: iOS once the user has answered,
     * Android below 13 where no prompt exists.
     */
    suspend fun canShowSystemPrompt(): Boolean

    /** Suspends until the platform can subscribe (iOS: until FCM has its registration token). */
    suspend fun subscribeToTopic(topic: String)

    suspend fun unsubscribeFromTopic(topic: String)
}

expect val pushNotificationsPlatformModule: Module
