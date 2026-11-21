package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.config.AppConfig

object OlxConfig {
    const val apiVersion = "2.0"
    const val authTokenPath = "open/oauth/token"
    const val defaultRefreshSafetyWindowSeconds = 60L

    val clientId: String
        get() = _currentOlxCountry.clientId

    val clientSecret: String
        get() = _currentOlxCountry.clientSecret

    // Changing scope = new data access. Update privacy policy pages before releasing:
    // https://sirelon.github.io/SellSnap/privacy-policy/
    // https://sirelon.github.io/SellSnap/terms-and-conditions/
    val scope: String
        get() = AppConfig.olxScope

    val authBaseUrl: String
        get() = _currentOlxCountry.authBaseUrl

    val apiBaseUrl: String
        get() = _currentOlxCountry.apiBaseUrl

    /**
     * The `redirect_uri` sent to OLX: an HTTPS page (`docs/olx-callback/`) that forwards OLX's
     * `code` and `state` to [appCallbackScheme]. OLX accepts only HTTPS redirect URIs
     * (developer.olx.<tld>/docs/faq, "How do I set or change the redirect URI (callback URL)?");
     * with the custom scheme, OLX's login never returned to the app. The URI must also be
     * registered under "Your apps" on each market's developer portal.
     * Changing it? See SIR-178 (App Links migration) and update policy pages above.
     */
    val redirectUri: String
        get() = AppConfig.olxRedirectUri

    /** The scheme the forwarding page opens; Android's intent filter and iOS's auth session catch it. */
    const val appCallbackScheme = "selolxai"
}
