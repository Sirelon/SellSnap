package com.sirelon.sellsnap.platform

import kotlinx.browser.window

actual fun openUrl(url: String) {
    if (url.isBlank()) return
    window.open(url, "_blank")
}

// No store of its own; the Play listing is the canonical SellSnap page.
actual fun storePageUrl(): String = "https://play.google.com/store/apps/details?id=com.sirelon.sellsnap"
