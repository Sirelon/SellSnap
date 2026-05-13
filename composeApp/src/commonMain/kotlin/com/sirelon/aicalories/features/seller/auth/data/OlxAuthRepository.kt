package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthCallback
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAuthorizationRequest
import com.sirelon.sellsnap.features.seller.auth.domain.OlxPendingAuthSession
import com.sirelon.sellsnap.features.seller.auth.domain.OlxSessionState
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import com.sirelon.sellsnap.features.seller.auth.domain.SellerSessionMode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.uuid.Uuid

class OlxAuthRepository(
    private val httpClient: HttpClient,
    private val credentialsProvider: OlxCredentialsProvider,
    private val tokenStore: OlxTokenStore,
    private val authSessionStore: OlxAuthSessionStore,
    private val redirectHandler: OlxRedirectHandler,
    private val guestModeStore: GuestModeStore,
    private val errorParser: OlxRemoteErrorParser,
) {
    suspend fun createAuthorizationRequest(): OlxAuthorizationRequest {
        val state = Uuid.random().toString()
        val redirectUri = redirectHandler.buildRedirectUri()
        val clientId = credentialsProvider.getClientId()
        val url = URLBuilder().takeFrom(OlxConfig.authBaseUrl).apply {
            parameters.apply {
                append("client_id", clientId)
                append("response_type", "code")
                append("state", state)
                append("scope", OlxConfig.scope)
                append("redirect_uri", redirectUri)
            }
        }.buildString()

        val request = OlxAuthorizationRequest(
            url = url,
            state = state,
            redirectUri = redirectUri,
            scope = OlxConfig.scope,
        )

        authSessionStore.write(
            session = OlxPendingAuthSession(
                state = state,
                redirectUri = redirectUri,
                createdAtEpochSeconds = currentEpochSeconds(),
            ),
        )
        return request
    }

    suspend fun completeAuthorization(callbackUrl: String): Result<OlxTokens> = runCatching {
        val callback = redirectHandler.parseCallback(callbackUrl)
        val pendingSession = authSessionStore.read()
            ?: throw OlxApiException(OlxApiError.InvalidState("No active OLX authorization session was found."))

        validateCallback(callback, pendingSession)

        val tokenResponse = exchangeAuthorizationCode(
            callback = callback,
            redirectUri = pendingSession.redirectUri,
        )
        tokenStore.write(tokenResponse)
        authSessionStore.clear()
        guestModeStore.setGuest(false)
        tokenResponse
    }.onFailure {
        authSessionStore.clear()
    }

    suspend fun refreshIfNeeded(force: Boolean = false): Result<OlxTokens> = runCatching {
        val existingTokens = tokenStore.read()
            ?: throw OlxApiException(OlxApiError.InvalidToken("OLX user is not connected yet."))

        if (!force && !existingTokens.isExpired(currentEpochSeconds(), OlxConfig.defaultRefreshSafetyWindowSeconds)) {
            return@runCatching existingTokens
        }

        val refreshedTokens = refreshAccessToken(existingTokens)
        tokenStore.write(refreshedTokens)
        refreshedTokens
    }.onFailure { error ->
        handleTerminalRefreshFailure(error, tokenStore, authSessionStore)
    }

    suspend fun requireAccessToken(forceRefresh: Boolean = false): Result<String> {
        return refreshIfNeeded(force = forceRefresh).map { it.accessToken }
    }

    suspend fun logout() {
        tokenStore.clear()
        authSessionStore.clear()
        guestModeStore.setGuest(false)
    }

    suspend fun enterGuestMode() {
        guestModeStore.setGuest(true)
    }

    suspend fun exitGuestMode() {
        guestModeStore.setGuest(false)
    }

    suspend fun currentSession(): OlxSessionState {
        val isGuestModeEnabled = guestModeStore.isGuest()
        val tokens = tokenStore.read()
        val mode = when {
            isGuestModeEnabled -> SellerSessionMode.Guest
            tokens != null -> SellerSessionMode.Authenticated
            else -> SellerSessionMode.Unauthenticated
        }
        return OlxSessionState(
            mode = mode,
            accessTokenExpiresAtEpochSeconds = tokens?.expiresAtEpochSeconds,
        )
    }

    private fun validateCallback(callback: OlxAuthCallback, pendingSession: OlxPendingAuthSession) {
        if (callback.error != null) {
            throw OlxApiException(
                OlxApiError.Unknown(
                    callback.errorDescription ?: "OLX returned an authorization error: ${callback.error}.",
                ),
            )
        }

        if (callback.code.isNullOrBlank()) {
            throw OlxApiException(OlxApiError.MissingCode())
        }

        if (callback.state.isNullOrBlank() || callback.state != pendingSession.state) {
            throw OlxApiException(OlxApiError.InvalidState())
        }
    }

    private suspend fun exchangeAuthorizationCode(
        callback: OlxAuthCallback,
        redirectUri: String,
    ): OlxTokens {
        val response = httpClient.post("/api/${OlxConfig.authTokenPath}") {
            contentType(ContentType.Application.Json)
            setBody(
                TokenRequest(
                    grantType = "authorization_code",
                    clientId = credentialsProvider.getClientId(),
                    clientSecret = credentialsProvider.getClientSecret(),
                    scope = OlxConfig.scope,
                    code = callback.code,
                    redirectUri = redirectUri,
                ),
            )
        }

        if (!response.status.isSuccess()) {
            throw errorParser.parse(response.status, response.bodyAsText())
        }

        return response.body<TokenResponse>().toDomain(currentEpochSeconds())
    }

    private suspend fun refreshAccessToken(tokens: OlxTokens): OlxTokens {
        val refreshToken = tokens.refreshToken
            ?: throw OlxApiException(OlxApiError.InvalidGrant("No OLX refresh token is available."))

        val response = httpClient.post("/api/${OlxConfig.authTokenPath}") {
            contentType(ContentType.Application.Json)
            setBody(
                TokenRequest(
                    grantType = "refresh_token",
                    clientId = credentialsProvider.getClientId(),
                    clientSecret = credentialsProvider.getClientSecret(),
                    refreshToken = refreshToken,
                ),
            )
        }

        if (!response.status.isSuccess()) {
            throw errorParser.parse(response.status, response.bodyAsText())
        }

        return response.body<TokenResponse>().toDomain(currentEpochSeconds())
    }

    private fun currentEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

    @Serializable
    private class TokenRequest(
        @SerialName("grant_type") val grantType: String,
        @SerialName("client_id") val clientId: String,
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("scope") val scope: String? = null,
        @SerialName("code") val code: String? = null,
        @SerialName("redirect_uri") val redirectUri: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
    )

    @Serializable
    private class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresInSeconds: Long,
        @SerialName("token_type") val tokenType: String,
        @SerialName("scope") val scope: String,
    ) {
        fun toDomain(issuedAtEpochSeconds: Long): OlxTokens = OlxTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = expiresInSeconds,
            tokenType = tokenType,
            scope = scope,
            issuedAtEpochSeconds = issuedAtEpochSeconds,
        )
    }
}

internal suspend fun handleTerminalRefreshFailure(
    error: Throwable,
    tokenStore: OlxTokenStore,
    authSessionStore: OlxAuthSessionStore,
) {
    val olxError = (error as? OlxApiException)?.error
    if (olxError is OlxApiError.InvalidGrant || olxError is OlxApiError.InvalidToken) {
        tokenStore.clear()
        authSessionStore.clear()
    }
}
