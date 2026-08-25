package com.sirelon.sellsnap.analytics

/**
 * Implemented by navigation destinations so screen_view reports a stable name. Falling back to
 * `KClass.simpleName` isn't safe here: R8 renames destination classes in release builds
 * (isMinifyEnabled = true), which would turn screen names into obfuscated single letters.
 */
interface AnalyticsScreen {
    val screenName: String
}
