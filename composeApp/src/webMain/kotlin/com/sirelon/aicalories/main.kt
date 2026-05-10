package com.sirelon.sellsnap

import androidx.compose.ui.window.ComposeViewport
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthCallbackBridge
import kotlinx.browser.window

fun main() {
    publishOlxCallbackIfPresent()
    ComposeViewport {
        App()
    }
}

private fun publishOlxCallbackIfPresent() {
    val href = window.location.href
    if (href.contains("code=") || href.contains("error=")) {
        OlxAuthCallbackBridge.publishCallback(href)
    }
}
