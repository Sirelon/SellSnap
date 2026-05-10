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

    val scope: String
        get() = AppConfig.olxScope

    val authBaseUrl: String
        get() = AppConfig.olxAuthBaseUrl

    const val apiBaseUrl = "https://www.olx.ua/api/partner/"

    val redirectUri: String
        get() = AppConfig.olxRedirectUri
}
