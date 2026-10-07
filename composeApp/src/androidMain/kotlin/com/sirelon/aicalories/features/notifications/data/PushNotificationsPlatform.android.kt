package com.sirelon.sellsnap.features.notifications.data

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import org.koin.core.module.Module
import org.koin.dsl.module

internal class AndroidPushNotificationsPlatform(
    private val context: Context,
) : PushNotificationsPlatform {

    override suspend fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    // POST_NOTIFICATIONS and its prompt exist from Android 13 (API 33); below that, notifications
    // are on by default and there is nothing to ask.
    override suspend fun canShowSystemPrompt(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun subscribeToTopic(topic: String) {
        Firebase.messaging.subscribeToTopic(topic)
    }

    override fun unsubscribeFromTopic(topic: String) {
        Firebase.messaging.unsubscribeFromTopic(topic)
    }
}

actual val pushNotificationsPlatformModule: Module = module {
    single<PushNotificationsPlatform> { AndroidPushNotificationsPlatform(context = get()) }
}
