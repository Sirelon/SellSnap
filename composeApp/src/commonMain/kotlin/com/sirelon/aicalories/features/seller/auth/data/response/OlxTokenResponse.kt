package com.sirelon.sellsnap.features.seller.auth.data.response

import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body of a successful OLX token-endpoint call (`authorization_code` and `refresh_token` grants).
 * Inferred from the fields the app has always read; OLX does not send an issue time, so the
 * caller supplies it.
 */
@Serializable
internal class OlxTokenResponse(
    @SerialName("access_token")
    val accessToken: String?,

    @SerialName("refresh_token")
    val refreshToken: String?,

    @SerialName("expires_in")
    val expiresInSeconds: Long?,

    @SerialName("token_type")
    val tokenType: String?,

    @SerialName("scope")
    val scope: String?,
) {
    /** Throws when OLX answered 2xx without an access token or a lifetime: nothing usable to store. */
    fun toDomain(issuedAtEpochSeconds: Long): OlxTokens = OlxTokens(
        accessToken = requireNotNull(accessToken) { "OLX token response has no access_token" },
        refreshToken = refreshToken,
        expiresInSeconds = requireNotNull(expiresInSeconds) { "OLX token response has no expires_in" },
        tokenType = tokenType.orEmpty(),
        scope = scope.orEmpty(),
        issuedAtEpochSeconds = issuedAtEpochSeconds,
    )
}
