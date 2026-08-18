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
