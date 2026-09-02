package com.sirelon.sellsnap.features.media

import android.content.Context
import android.net.Uri
import com.mohamedrejeb.calf.io.KmpFile
import com.mohamedrejeb.calf.io.initializeKmpFileContext

/**
 * Calf's KmpFile I/O calls (getName, readByteArray, ...) need this global context, normally set
 * as a side effect of rememberFilePickerLauncher composing on the picker screen. Photos shared in
 * via SharedImagesBridge can reach GenerateAdViewModel before that composable ever runs, so it
 * must be primed eagerly - otherwise the first KmpFile access throws IllegalStateException.
 */
fun initAndroidSharedImages(context: Context) {
    initializeKmpFileContext(context)
}

fun publishSharedImageUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    SharedImagesBridge.publish(uris.map(::KmpFile))
}
