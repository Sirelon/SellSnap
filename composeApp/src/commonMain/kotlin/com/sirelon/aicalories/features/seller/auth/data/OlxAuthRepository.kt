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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * OAuth/session-mode concern only (SIR-83 foundation): validates and exchanges an OLX
 * authorization callback for tokens, and reports session mode (Authenticated/Guest/
 * Unauthenticated) from the multi-account [accountStore] rather than a single token blob. Does
 * NOT decide which stored account a freshly exchanged token belongs to, or persist it - that
 * dedupe-then-persist sequencing lives one layer up in
 * `SellerAccountRepository.addAccount`, which needs a `users/me` call this class intentionally
 * has no dependency on (`OlxApiClient` is a REST-resource concern, not an OAuth one).
 */
class OlxAuthRepository internal constructor(
    private val httpClient: HttpClient,
    private val credentialsProvider: OlxCredentialsProvider,
    private val accountStore: OlxAccountStore,
    private val countryStore: OlxCountryStore,
    private val authSessionStore: OlxAuthSessionStore,
    private val redirectHandler: OlxRedirectHandler,
    private val guestModeStore: GuestModeStore,
    private val errorParser: OlxRemoteErrorParser,
) {
    private val _sessionModeUpdates = MutableSharedFlow<SellerSessionMode>(replay = 0)
    val sessionModeFlow: Flow<SellerSessionMode> = _sessionModeUpdates.asSharedFlow()

    suspend fun createAuthorizationRequest(forceReauth: Boolean = false): OlxAuthorizationRequest {
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
                if (forceReauth) {
                    // TRD §1 route 1 (cheapest, try first): a standard-OIDC force-reauthentication
                    // parameter. OLX's partner API is plain OAuth 2.0 rather than OIDC, so this is
                    // not confirmed to be honoured - if ignored, the platform-level launcher
                    // mechanisms (iOS ephemeral session; Android logout-URL preload) are the
                    // fallback, and the add-account dedupe now handles an unforced re-auth
                    // gracefully either way (SellerAccountRepository.addAccount).
                    append("prompt", "login")
                    append("max_age", "0")
                }
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

    /**
     * Validates the pending session/state and exchanges the authorization code for tokens.
     * Deliberately does NOT persist anything, or touch guest mode/session-mode: the caller
     * (`SellerAccountRepository.addAccount`) must confirm which OLX user the token belongs to
     * (dedupe by olxUserId) before deciding where it lands and whether to call [markAuthenticated].
     */
    internal suspend fun exchangeAuthorizationCallback(callbackUrl: String): OlxTokens {
        try {
            val callback = redirectHandler.parseCallback(callbackUrl)
            val pendingSession = authSessionStore.read()
                ?: throw OlxApiException(OlxApiError.InvalidState("No active OLX authorization session was found."))

            validateCallback(callback, pendingSession)

            val tokens = exchangeAuthorizationCode(callback, pendingSession.redirectUri)
            authSessionStore.clear()
            return tokens
        } catch (throwable: Throwable) {
            authSessionStore.clear()
            throw throwable
        }
    }

    /** Called by `SellerAccountRepository` once a freshly connected/reconnected account is persisted. */
    internal suspend fun markAuthenticated() {
        guestModeStore.setGuest(false)
        _sessionModeUpdates.emit(SellerSessionMode.Authenticated)
    }

    suspend fun logout() {
        authSessionStore.clear()
        guestModeStore.setGuest(false)
        _sessionModeUpdates.emit(SellerSessionMode.Unauthenticated)
    }

    suspend fun enterGuestMode() {
        guestModeStore.setGuest(true)
        _sessionModeUpdates.emit(SellerSessionMode.Guest)
    }

    suspend fun exitGuestMode() {
        guestModeStore.setGuest(false)
        _sessionModeUpdates.emit(SellerSessionMode.Unauthenticated)
    }

    /**
     * Source of truth for session mode (F4/D7 fix): Authenticated means "at least one account is
     * on file for the active country", regardless of whether its token is currently healthy - a
     * dead token means NeedsReconnect, not logged out. Never calls the network.
     */
    suspend fun currentSession(): OlxSessionState {
        val isGuestModeEnabled = guestModeStore.isGuest()
        val activeAccount = activeAccountForCurrentCountry()
        val mode = when {
            isGuestModeEnabled -> SellerSessionMode.Guest
            activeAccount != null -> SellerSessionMode.Authenticated
            else -> SellerSessionMode.Unauthenticated
        }
        return OlxSessionState(
            mode = mode,
            accessTokenExpiresAtEpochSeconds = activeAccount?.tokens?.expiresAtEpochSeconds,
        )
    }

    private fun activeAccountForCurrentCountry(): OlxAccountRecord? {
        val record = accountStore.recordFlow.value
        val activeIndex = record.activeByCountry[countryStore.current.code] ?: return null
        return record.accounts.find { it.localIndex == activeIndex }
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

    private fun currentEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

    @Serializable
    private class TokenRequest(
        @SerialName("grant_type") val grantType: String,
        @SerialName("client_id") val clientId: String,
        @SerialName("client_secret") val clientSecret: String,
        @SerialName("scope") val scope: String? = null,
        @SerialName("code") val code: String? = null,
        @SerialName("redirect_uri") val redirectUri: String? = null,
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
