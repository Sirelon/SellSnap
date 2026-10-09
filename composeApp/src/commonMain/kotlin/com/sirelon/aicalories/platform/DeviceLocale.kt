package com.sirelon.sellsnap.platform

expect fun getDeviceCountryCode(): String?

expect fun getDeviceLanguageCode(): String?

/**
 * The language the app renders for [languageCode]: Ukrainian for a Russian-locale device
 * (`values-ru` is a copy of `values-uk`), the code itself otherwise. Server-side content and push
 * topics follow the same rule so they match what the UI shows.
 */
fun appLanguageCode(languageCode: String): String = if (languageCode == "ru") "uk" else languageCode
