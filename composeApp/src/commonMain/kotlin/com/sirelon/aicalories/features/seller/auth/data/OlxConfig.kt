package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.config.AppConfig

object OlxConfig {
    const val apiVersion = "2.0"
    const val authTokenPath = "open/oauth/token"
    const val defaultRefreshSafetyWindowSeconds = 60L

    val clientId: String
        get() = AppConfig.olxClientId

    val clientSecret: String
        get() = AppConfig.olxClientSecret

    // Changing scope = new data access. Update privacy policy pages before releasing:
    // https://sirelon.github.io/SellSnap/privacy-policy/
    // https://sirelon.github.io/SellSnap/terms-and-conditions/
    val scope: String
        get() = AppConfig.olxScope

    val authBaseUrl: String
        get() = _currentOlxCountry.authBaseUrl

    val apiBaseUrl: String
        get() = _currentOlxCountry.apiBaseUrl

    // Changing redirect scheme? See BUGS.md #2 (App Links migration) and update policy pages above.
    val redirectUri: String
        get() = AppConfig.olxRedirectUri
}
