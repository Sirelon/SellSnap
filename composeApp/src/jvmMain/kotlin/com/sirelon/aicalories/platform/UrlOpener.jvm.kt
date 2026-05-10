package com.sirelon.sellsnap.platform

import java.awt.Desktop
import java.net.URI

actual fun openUrl(url: String) {
    if (url.isBlank() || !Desktop.isDesktopSupported()) return
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }
}
