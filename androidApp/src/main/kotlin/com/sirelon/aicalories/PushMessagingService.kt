package com.sirelon.sellsnap

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Background `notification` messages are displayed by the FCM SDK itself; a tap launches
 * [MainActivity] with the message `data` as intent extras, which `MainActivity.openPushLink`
 * reads. This class only handles the foreground case, where the SDK does not display anything.
 *
 * Topic messages arrive only after a launch subscribed this device, and that launch also created
 * the [PUSH_CHANNEL_ID] channel, so the channel exists whenever this runs. The service can run in
 * a process where MainActivity never started, so it must not touch Koin, DataStore or compose
 * resources.
 */
// Only topics are used, and every launch subscribes them again (PushNotificationsRepository),
// so a new token needs no handling here.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class PushMessagingService : FirebaseMessagingService() {
    // notify() needs POST_NOTIFICATIONS on API 33+; areNotificationsEnabled() is checked first.
    @SuppressLint("MissingPermission")
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val id = System.currentTimeMillis().toInt()
        val openApp = Intent(this, MainActivity::class.java).apply {
            message.data.forEach { (key, value) -> putExtra(key, value) }
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            id,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val built = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(this).notify(id, built)
    }
}
