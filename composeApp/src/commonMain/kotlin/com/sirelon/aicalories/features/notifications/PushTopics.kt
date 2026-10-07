package com.sirelon.sellsnap.features.notifications

import com.sirelon.sellsnap.features.whatsnew.data.FALLBACK_LANGUAGE_CODE
import com.sirelon.sellsnap.platform.appLanguageCode

internal const val ALL_PUSH_TOPIC = "all"

// The languages the app ships strings for; a push audience exists for each.
private val PUSH_LANGUAGES = setOf("en", "uk", "pl", "pt", "ro", "bg", "kk")

/** `all-<lang>` for the language the app renders on this device; English when it has no audience. */
internal fun pushLanguageTopicFor(languageCode: String?): String {
    val language = languageCode?.lowercase()?.let(::appLanguageCode)
    return "$ALL_PUSH_TOPIC-${language?.takeIf { it in PUSH_LANGUAGES } ?: FALLBACK_LANGUAGE_CODE}"
}

/** Every topic this device belongs to: everyone, and everyone who reads [languageCode]. */
internal fun pushTopicsFor(languageCode: String?): List<String> =
    listOf(ALL_PUSH_TOPIC, pushLanguageTopicFor(languageCode))
