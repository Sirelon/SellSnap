package com.sirelon.sellsnap.analytics

interface Analytics {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
    fun setUserId(userId: String?)
    fun setUserProperty(name: String, value: String?)
    fun recordException(throwable: Throwable, message: String? = null)
    fun log(message: String)

    /** Enables or disables analytics collection. Off until the user opts in (consent). */
    fun setAnalyticsCollectionEnabled(enabled: Boolean)

    /** Enables or disables crash-reporting collection. On by default; the user may opt out. */
    fun setCrashlyticsCollectionEnabled(enabled: Boolean)
}

/**
 * This app is single-activity/single-ViewController Compose Multiplatform, so Firebase's
 * automatic per-Activity screen tracking never fires. Call this on every navigation change to
 * report screens manually using Firebase's reserved `screen_view` event and param names.
 */
fun Analytics.logScreenView(screenName: String) {
    logEvent("screen_view", mapOf("screen_name" to screenName, "screen_class" to screenName))
}
