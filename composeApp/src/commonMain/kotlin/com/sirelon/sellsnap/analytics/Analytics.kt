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
 * Rewrites parameter values into types Firebase actually stores.
 *
 * Firebase Analytics accepts only String, long and double event parameters. The gitlive adapter
 * maps a Kotlin `Boolean` onto `Bundle.putBoolean`, which the SDK then drops - so a boolean param
 * arrives absent rather than as `false`, which is indistinguishable from the event never carrying
 * it. Booleans become `"true"`/`"false"` so both values stay queryable, and so the same event
 * has the same parameter type on Android and iOS.
 *
 * A Kotlin `Long` has the same problem on iOS only: gitlive's iOS `logEvent` hands the parameter
 * map straight to `FIRAnalytics.logEventWithName(name:parameters:)` as `[String: Any]`, and a
 * boxed Kotlin `Long` crossing that Kotlin/Native - Objective-C boundary arrives as a `KotlinLong`
 * instance - technically an `NSNumber` subclass, but not the one Firebase's own parameter
 * validation recognizes, so it drops the parameter. A boxed `Int` crosses the same boundary as
 * `KotlinInt` and is accepted. Confirmed from the BigQuery export (`analytics_538260747`):
 * `duration_ms` (`Long`, from `GenerateAdViewModel`) has never once arrived on iOS across every
 * version from 2.2 to 3.3, while `Int` params such as `account_index` and `publish_count` arrive
 * normally as `int_value`. Android's `Bundle.putLong` has no such issue, but BigQuery's export
 * schema stores both `Int` and `Long` params in the same `int_value` column, so narrowing to
 * `Int` costs Android nothing observable. Every value this app logs (elapsed milliseconds, a day
 * count) is far below `Int.MAX_VALUE`, so the coercion below only guards against a value that
 * should never occur.
 *
 * Every platform [Analytics] implementation must apply this before handing params to Firebase.
 */
internal fun Map<String, Any>.normalizedForFirebase(): Map<String, Any> =
    mapValues { (_, value) ->
        when (value) {
            is Boolean -> value.toString()
            is Long -> value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            else -> value
        }
    }

/**
 * This app is single-activity/single-ViewController Compose Multiplatform, so Firebase's
 * automatic per-Activity screen tracking never fires. Call this on every navigation change to
 * report screens manually using Firebase's reserved `screen_view` event and param names.
 */
fun Analytics.logScreenView(screenName: String) {
    logEvent("screen_view", mapOf("screen_name" to screenName, "screen_class" to screenName))
}
