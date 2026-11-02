package com.sirelon.sellsnap.network

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpStatusCode
import org.koin.core.module.Module

/**
 * The Cloud Function that holds the OpenAI key (`functions/src/index.ts`). openai-kotlin resolves
 * `responses` against this URL, so the trailing slash is load-bearing.
 */
const val OPENAI_PROXY_BASE_URL = "https://europe-west1-sellsnap-6e85c.cloudfunctions.net/openai/v1/"

const val OPENAI_DIRECT_BASE_URL = "https://api.openai.com/v1/"

/** Header the proxy verifies with the Firebase Admin SDK before forwarding anything. */
const val APP_CHECK_HEADER = "X-Firebase-AppCheck"

/**
 * Source of Firebase App Check tokens. A null token means the platform has none to offer.
 * [forceRefresh] asks for a new attestation instead of the cached token; the proxy answering 401
 * is its one caller.
 */
fun interface AppCheckTokenProvider {
    suspend fun token(forceRefresh: Boolean): String?
}

/** Where ad generation sends its OpenAI requests and how it proves who is calling. */
class OpenAIEndpoint(
    val baseUrl: String,
    /** Sent as `Authorization: Bearer`. The proxy ignores it and injects the real key itself. */
    val bearerToken: String,
    /** Null where the platform has no App Check (desktop, web). */
    val appCheck: AppCheckTokenProvider?,
)

expect val openAIEndpointModule: Module

/**
 * Attaches an App Check token to every request so the proxy can verify the caller, and retries a
 * request the proxy answered 401 once with a freshly minted token. The SDK caches a token for about
 * an hour; one the proxy no longer accepts (clock skew, a provider re-registered after a reinstall)
 * otherwise fails every generation until the cache expires - Android showed 7 such failed POSTs in
 * October 2026. A second 401 is returned as is, so the seller gets the App Check message.
 */
fun appCheckHeaderPlugin(provider: AppCheckTokenProvider) = createClientPlugin("FirebaseAppCheck") {
    on(Send) { request ->
        val token = provider.token(forceRefresh = false)
        token?.let { request.headers[APP_CHECK_HEADER] = it }
        val call = proceed(request)
        if (call.response.status != HttpStatusCode.Unauthorized) return@on call
        val fresh = provider.token(forceRefresh = true)
        if (fresh == null || fresh == token) return@on call
        request.headers[APP_CHECK_HEADER] = fresh
        proceed(request)
    }
}
