package com.sirelon.sellsnap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Channel for all push messages. Must match `default_notification_channel_id` in the manifest. */
const val PUSH_CHANNEL_ID = "updates"

/** Key in the FCM `data` payload that carries the link to open on tap. */
const val PUSH_LINK_KEY = "link"

private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.sirelon.sellsnap"

/** Creates the push channel; calling it again with a new [name] renames the existing channel. */
fun createPushChannel(context: Context, name: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(PUSH_CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT),
    )
}

/** `store` opens this app's Play listing, an `https://` value opens as given, anything else is ignored. */
fun resolvePushLink(raw: String?): String? = when {
    raw == null -> null
    raw == "store" -> PLAY_STORE_URL
    raw.startsWith("https://") -> raw
    else -> null
}
