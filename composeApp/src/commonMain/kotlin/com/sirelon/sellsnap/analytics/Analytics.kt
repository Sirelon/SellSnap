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

/** Firebase drops an event parameter whose string value is longer than this. */
private const val FirebaseParamValueMaxLength = 100

/**
 * Stands in for the throwable a shown error does not have: a refusal decided in app code, or a
 * failure whose cause was dropped further down. Crashlytics groups non-fatals by type and stack,
 * so these can share an issue; the message - `"<screen>: <reason>"` - tells them apart.
 */
class ShownErrorException(message: String) : Exception(message)

/**
 * The one way to report a problem the seller was shown - a snackbar, an inline error, a failure
 * sheet. Every user-visible error goes through here, so a seller who cannot publish is never
 * found out by accident.
 *
 * Logs [AnalyticsEvents.ERROR_SHOWN] and a Crashlytics breadcrumb, then records a Crashlytics
 * non-fatal: [throwable] when there is one, otherwise a [ShownErrorException]. Crashlytics is the
 * channel that always arrives - Analytics events wait for consent.
 *
 * [screen] and [reason] are stable snake_case codes (`preview_ad`, `validation:district_id`),
 * never user text or anything identifying. [recordNonFatal] is false only for input-validation
 * nudges that are part of normal use, such as a title that is still too short.
 */
fun Analytics.reportShownError(
    screen: String,
    reason: String,
    throwable: Throwable? = null,
    recordNonFatal: Boolean = true,
) {
    val screenCode = screen.take(FirebaseParamValueMaxLength)
    val reasonCode = reason.take(FirebaseParamValueMaxLength)
    logEvent(AnalyticsEvents.ERROR_SHOWN, mapOf("screen" to screenCode, "reason" to reasonCode))
    log("${AnalyticsEvents.ERROR_SHOWN} $screenCode: $reasonCode")
    if (recordNonFatal) {
        recordException(throwable ?: ShownErrorException("$screenCode: $reasonCode"))
    }
}
