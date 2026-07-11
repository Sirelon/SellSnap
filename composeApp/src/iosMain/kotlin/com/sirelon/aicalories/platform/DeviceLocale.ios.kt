package com.sirelon.sellsnap.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

@Suppress("UNCHECKED_CAST")
actual fun getDeviceCountryCode(): String? {
    // AppleLanguages stores user-preferred BCP 47 tags like ["en-US", "uk-UA"]
    val langs = NSUserDefaults.standardUserDefaults.objectForKey("AppleLanguages") as? List<Any?>
    val first = langs?.firstOrNull() as? String ?: return null
    return first.substringAfter('-', "").lowercase().ifBlank { null }
}
