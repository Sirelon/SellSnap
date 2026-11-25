package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.auth.data.response.OlxTokenResponse
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

fun createOlxHttpClient(engine: HttpClientEngine? = null): HttpClient {
    return if (engine != null) {
        HttpClient(engine, commonOlxHttpClientConfig())
    } else {
        HttpClient(commonOlxHttpClientConfig())
    }
}

/**
 * Multi-account aware (SIR-83): [loadTokens]/[refreshTokens] resolve whichever account is active
 * for [countryStore]'s current country inside [accountStore], instead of a single global token
 * blob. A terminal refresh failure (invalid_grant/invalid_token) marks ONLY that one account
 * NeedsReconnect via [OlxAccountStore.markNeedsReconnect] - other accounts in the store, and other
 * countries, are untouched. [OlxAccountStore]'s own mutex (not a second one here) is what keeps
 * this safe against a concurrent keep-alive refresh touching the same account.
 */
internal fun createOlxAuthorizedHttpClient(
    authRefreshClient: HttpClient,
    credentialsProvider: OlxCredentialsProvider,
    accountStore: OlxAccountStore,
    countryStore: OlxCountryStore,
    errorParser: OlxRemoteErrorParser,
    engine: HttpClientEngine? = null,
): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        commonOlxHttpClientConfig().invoke(this)
        install(Auth) {
            bearer {
                loadTokens {
                    activeAccount(accountStore, countryStore)?.tokens?.toBearerTokens()
                }
                refreshTokens {
                    val activeLocalIndex = activeAccount(accountStore, countryStore)?.localIndex
                        ?: return@refreshTokens null
                    try {
                        val refreshedTokens = refreshOlxTokens(
                            client = authRefreshClient,
                            tokenEndpointUrl = "/api/${OlxConfig.authTokenPath}",
                            clientId = credentialsProvider.getClientId(),
                            clientSecret = credentialsProvider.getClientSecret(),
                            refreshToken = oldTokens?.refreshToken,
                            errorParser = errorParser,
                        )
                        if (refreshedTokens == null) {
                            null
                        } else {
                            accountStore.updateTokens(activeLocalIndex, refreshedTokens, nowEpochSeconds())
                            refreshedTokens.toBearerTokens()
                        }
                    } catch (exception: OlxApiException) {
                        if (exception.error is OlxApiError.InvalidGrant || exception.error is OlxApiError.InvalidToken) {
                            accountStore.markNeedsReconnect(activeLocalIndex)
                        }
                        throw exception
                    }
                }
            }
        }
    }

    return if (engine != null) {
        HttpClient(engine, configure)
    } else {
        HttpClient(configure)
    }
}

/**
 * Carries the app's own token instead of a seller's: OAuth `client_credentials`, scope `v2 read`,
 * minted for the current market. It reads OLX's catalog (categories, category suggestion,
 * attributes) when no seller token works, so a seller whose OLX connection expired still gets a
 * full listing (SIR-201).
 *
 * developer.olx.ua swagger v2: `components.securitySchemes.access_token.flows.clientCredentials`
 * (tokenUrl `/api/open/oauth/token`), and `/categories`, `/categories/suggestion` and
 * `/categories/{categoryId}/attributes` ask for `access_token` with `v2`, `read`, naming no flow.
 * Live on 2026-11-25 in pt, ro, pl, ua, bg and kz: the grant answered, all three endpoints
 * answered 200 with the minted token, and a request with no token got 401. The token lives 900 s
 * (86400 s in kz) and comes without a refresh token, so a 401 simply mints a new one. That also
 * covers a market switch: the old market's token gets a 401 on the new market's host.
 */
internal fun createOlxAppHttpClient(
    authRefreshClient: HttpClient,
    credentialsProvider: OlxCredentialsProvider,
    errorParser: OlxRemoteErrorParser,
    engine: HttpClientEngine? = null,
): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        commonOlxHttpClientConfig().invoke(this)
        install(Auth) {
            bearer {
                loadTokens { mintOlxAppToken(authRefreshClient, credentialsProvider, errorParser).toBearerTokens() }
                refreshTokens { mintOlxAppToken(authRefreshClient, credentialsProvider, errorParser).toBearerTokens() }
            }
        }
    }

    return if (engine != null) {
        HttpClient(engine, configure)
    } else {
        HttpClient(configure)
    }
}

private suspend fun mintOlxAppToken(
    client: HttpClient,
    credentialsProvider: OlxCredentialsProvider,
    errorParser: OlxRemoteErrorParser,
): OlxTokens {
    val response = client.post("/api/${OlxConfig.authTokenPath}") {
        contentType(ContentType.Application.Json)
        setBody(
            ClientCredentialsRequest(
                grantType = "client_credentials",
                clientId = credentialsProvider.getClientId(),
                clientSecret = credentialsProvider.getClientSecret(),
                scope = "v2 read",
            ),
        )
    }

    if (!response.status.isSuccess()) {
        throw errorParser.parse(response.status, response.bodyAsText())
    }

    return response.body<OlxTokenResponse>().toDomain(issuedAtEpochSeconds = nowEpochSeconds())
}

private fun activeAccount(accountStore: OlxAccountStore, countryStore: OlxCountryStore): OlxAccountRecord? {
    val record = accountStore.recordFlow.value
    val activeIndex = record.activeByCountry[countryStore.current.code] ?: return null
    return record.accounts.find { it.localIndex == activeIndex }
}

private fun commonOlxHttpClientConfig(): HttpClientConfig<*>.() -> Unit = {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            },
        )
    }
    install(Logging) {
        level = LogLevel.NONE
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 90_000
    }
    install(DefaultRequest) {
        header(HttpHeaders.Accept, ContentType.Application.Json)
        header("Version", OlxConfig.apiVersion)
    }
    expectSuccess = false
    defaultRequest {
        url(OlxConfig.apiBaseUrl)
    }
}

/**
 * Single implementation of "POST a refresh_token grant to OLX and parse the response". Used both
 * by the bearer plugin's reactive 401 refresh above (relative [tokenEndpointUrl], resolved against
 * the client's baked-in current-country base url) and by SellerAccountRepository's keep-alive
 * sweep (absolute, per-account-country [tokenEndpointUrl]/credentials, since that sweep can touch
 * accounts for a country other than whichever one is "current" globally).
 */
internal suspend fun refreshOlxTokens(
    client: HttpClient,
    tokenEndpointUrl: String,
    clientId: String,
    clientSecret: String,
    refreshToken: String?,
    errorParser: OlxRemoteErrorParser,
): OlxTokens? {
    if (refreshToken.isNullOrBlank()) return null

    val response = client.post(tokenEndpointUrl) {
        contentType(ContentType.Application.Json)
        setBody(
            RefreshTokenRequest(
                grantType = "refresh_token",
                clientId = clientId,
                clientSecret = clientSecret,
                refreshToken = refreshToken,
            ),
        )
    }

    if (!response.status.isSuccess()) {
        throw errorParser.parse(response.status, response.bodyAsText())
    }

    return response.body<OlxTokenResponse>().toDomain(issuedAtEpochSeconds = nowEpochSeconds())
}

private fun nowEpochSeconds(): Long = Clock.System.now().toEpochMilliseconds() / 1000

private fun OlxTokens.toBearerTokens(): BearerTokens = BearerTokens(
    accessToken = accessToken,
    refreshToken = refreshToken ?: "",
)

@Serializable
private class RefreshTokenRequest(
    @SerialName("grant_type") val grantType: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
private class ClientCredentialsRequest(
    @SerialName("grant_type") val grantType: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("scope") val scope: String,
)
