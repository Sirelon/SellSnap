package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.platform.PlatformTargets
import io.ktor.http.Url

class DefaultOlxRedirectHandler : OlxRedirectHandler {
    override fun buildRedirectUri(platform: PlatformTargets): String = OlxConfig.redirectUri

    override fun parseCallback(url: String): OlxAuthCallback {
        val parsedUrl = Url(url)
        return OlxAuthCallback(
            code = parsedUrl.parameters["code"],
            state = parsedUrl.parameters["state"],
            error = parsedUrl.parameters["error"],
            errorDescription = parsedUrl.parameters["error_description"],
        )
    }
}
