package com.sirelon.sellsnap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.sirelon.sellsnap.platform.openUrl

/** Channel for all push messages. Must match `default_notification_channel_id` in the manifest. */
const val PUSH_CHANNEL_ID = "updates"

/** Key in the FCM `data` payload that carries the link to open on tap. */
const val PUSH_LINK_KEY = "link"

private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.sirelon.sellsnap"
private const val PLAY_STORE_PACKAGE = "com.android.vending"

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

/**
 * Opens a resolved push link. A Play listing goes to the Play Store app directly, as Google's
 * "Linking to Google Play" guide does with `setPackage`; otherwise a device where Play does not own
 * play.google.com links opens the web page in a browser (seen on an emulator). Falls back to the
 * browser when the Play Store is not installed.
 */
fun openPushUrl(context: Context, url: String) {
    if (url.startsWith("https://play.google.com/")) {
        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(PLAY_STORE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(playIntent)
            return
        } catch (_: ActivityNotFoundException) {
            // No Play Store on this device: the browser below still shows the listing.
        }
    }
    openUrl(url)
}
