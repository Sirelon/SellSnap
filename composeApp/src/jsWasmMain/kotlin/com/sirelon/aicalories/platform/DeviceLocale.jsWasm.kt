package com.sirelon.sellsnap.platform

import kotlinx.browser.window

actual fun getDeviceCountryCode(): String? {
    val lang = window.navigator.language.ifBlank { return null }
    // BCP 47 format: "en-US", "uk-UA", "pl" — extract the region subtag after '-'
    return lang.substringAfter('-', "").lowercase().ifBlank { null }
}
