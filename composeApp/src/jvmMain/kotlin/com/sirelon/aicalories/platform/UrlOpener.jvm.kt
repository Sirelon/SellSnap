package com.sirelon.sellsnap.platform

import java.awt.Desktop
import java.net.URI

actual fun openUrl(url: String) {
    if (url.isBlank() || !Desktop.isDesktopSupported()) return
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }
}

// No store of its own; the Play listing is the canonical SellSnap page.
actual fun storePageUrl(): String = "https://play.google.com/store/apps/details?id=com.sirelon.sellsnap"
