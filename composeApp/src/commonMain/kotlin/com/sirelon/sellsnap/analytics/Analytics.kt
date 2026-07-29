package com.sirelon.sellsnap.analytics

interface Analytics {
    fun logEvent(name: String, params: Map<String, Any> = emptyMap())
    fun setUserId(userId: String?)
    fun setUserProperty(name: String, value: String?)
    fun recordException(throwable: Throwable, message: String? = null)
    fun log(message: String)

    /** Enables or disables analytics + crash-reporting collection. Off until the user consents. */
    fun setCollectionEnabled(enabled: Boolean)
}
