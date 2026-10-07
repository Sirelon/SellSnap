package com.sirelon.sellsnap.features.notifications.data

import org.koin.core.module.Module
import org.koin.dsl.module

// Desktop has no Firebase app and no push channel, so notifications are never available here.
internal class NoOpPushNotificationsPlatform : PushNotificationsPlatform {
    override suspend fun notificationsEnabled(): Boolean = false

    override suspend fun canShowSystemPrompt(): Boolean = false

    override suspend fun subscribeToTopic(topic: String) = Unit

    override suspend fun unsubscribeFromTopic(topic: String) = Unit
}

actual val pushNotificationsPlatformModule: Module = module {
    single<PushNotificationsPlatform> { NoOpPushNotificationsPlatform() }
}
