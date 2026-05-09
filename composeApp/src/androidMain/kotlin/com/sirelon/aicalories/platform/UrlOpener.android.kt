package com.sirelon.sellsnap.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

private var appContext: Context? = null

fun initAndroidUrlOpener(context: Context) {
    appContext = context.applicationContext
}

actual fun openUrl(url: String) {
    if (url.isBlank()) return
    val context = appContext ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
