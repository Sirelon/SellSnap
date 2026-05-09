package com.sirelon.sellsnap.features.seller.auth.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OlxTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long,
    @SerialName("token_type") val tokenType: String,
    @SerialName("scope") val scope: String,
    @SerialName("issued_at_epoch_seconds") val issuedAtEpochSeconds: Long,
) {
    val expiresAtEpochSeconds: Long
        get() = issuedAtEpochSeconds + expiresInSeconds

    fun isExpired(nowEpochSeconds: Long, safetyWindowSeconds: Long = 0L): Boolean {
        return expiresAtEpochSeconds <= nowEpochSeconds + safetyWindowSeconds
    }
}

data class OlxAuthorizationRequest(
    val url: String,
    val state: String,
    val redirectUri: String,
    val scope: String,
)

data class OlxAuthCallback(
    val code: String? = null,
    val state: String? = null,
    val error: String? = null,
    val errorDescription: String? = null,
)

@Serializable
data class OlxPendingAuthSession(
    val state: String,
    val redirectUri: String,
    val createdAtEpochSeconds: Long,
)

enum class SellerSessionMode {
    Authenticated,
    Guest,
    Unauthenticated,
}

data class OlxSessionState(
    val mode: SellerSessionMode,
    val isRefreshing: Boolean = false,
    val accessTokenExpiresAtEpochSeconds: Long? = null,
    val lastError: String? = null,
) {
    val isAuthorized: Boolean get() = mode == SellerSessionMode.Authenticated
}

data class OlxUser(
    val id: Long,
    val email: String,
    val status: String,
    val name: String,
    val phone: String,
    val createdAt: String,
    val lastLoginAt: String,
    val avatar: String?,
    val isBusiness: Boolean,
)

sealed interface OlxLaunchResult {
    data object Opened : OlxLaunchResult
    data class Unsupported(val reason: String) : OlxLaunchResult
}

// Language policy: [userMessage] is a stable, English developer-facing diagnostic string
// used for logging and OlxApiException.message. User-visible copy must come from
// localized string resources resolved in the presentation layer.
sealed interface OlxApiError {
    val userMessage: String

    data class MissingCode(
        override val userMessage: String = "OLX did not return an authorization code.",
    ) : OlxApiError

    data class InvalidState(
        override val userMessage: String = "OLX authorization state did not match the active session.",
    ) : OlxApiError

    data class InvalidClient(
        override val userMessage: String = "OLX client credentials are invalid or inactive.",
    ) : OlxApiError

    data class InvalidGrant(
        override val userMessage: String = "OLX authorization code or refresh token is invalid.",
    ) : OlxApiError

    data class InvalidToken(
        override val userMessage: String = "OLX access token is invalid or expired.",
    ) : OlxApiError

    data class InsufficientScope(
        override val userMessage: String = "The current OLX token does not have enough scope.",
    ) : OlxApiError

    data class NetworkFailure(
        override val userMessage: String,
    ) : OlxApiError

    data class Unknown(
        override val userMessage: String,
    ) : OlxApiError

    data class ValidationError(
        val field: String,
        val fieldDetail: String,
        override val userMessage: String = "$field: $fieldDetail",
    ) : OlxApiError

    data class RateLimited(
        override val userMessage: String = "Too many requests. Please wait before posting again.",
    ) : OlxApiError
}

class OlxApiException(val error: OlxApiError) : IllegalStateException(error.userMessage)
