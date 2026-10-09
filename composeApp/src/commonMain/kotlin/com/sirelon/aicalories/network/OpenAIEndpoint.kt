package com.sirelon.sellsnap.network

import io.ktor.client.plugins.api.createClientPlugin
import org.koin.core.module.Module

/**
 * The Cloud Function that holds the OpenAI key (`functions/src/index.ts`). openai-kotlin resolves
 * `responses` against this URL, so the trailing slash is load-bearing.
 */
const val OPENAI_PROXY_BASE_URL = "https://europe-west1-sellsnap-6e85c.cloudfunctions.net/openai/v1/"

const val OPENAI_DIRECT_BASE_URL = "https://api.openai.com/v1/"

/** Header the proxy verifies with the Firebase Admin SDK before forwarding anything. */
const val APP_CHECK_HEADER = "X-Firebase-AppCheck"

/** Source of Firebase App Check tokens. A null token means the platform has none to offer. */
fun interface AppCheckTokenProvider {
    suspend fun token(): String?
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

/** Attaches a fresh App Check token to every request so the proxy can verify the caller. */
fun appCheckHeaderPlugin(provider: AppCheckTokenProvider) = createClientPlugin("FirebaseAppCheck") {
    onRequest { request, _ ->
        provider.token()?.let { request.headers.append(APP_CHECK_HEADER, it) }
    }
}
