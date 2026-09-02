package com.sirelon.sellsnap.features.media

import com.mohamedrejeb.calf.io.KmpFile
import platform.Foundation.NSURL

/**
 * Called from iOSApp.swift when the host app is opened via the ShareExtension's
 * selolxai://share callback, with the file paths it wrote into the shared App Group container.
 */
fun publishSharedImagePaths(paths: List<String>) {
    if (paths.isEmpty()) return
    val files = paths.map { path -> KmpFile(url = NSURL.fileURLWithPath(path)) }
    SharedImagesBridge.publish(files)
}
