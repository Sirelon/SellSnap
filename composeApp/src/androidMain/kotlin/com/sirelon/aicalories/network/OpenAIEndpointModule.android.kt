package com.sirelon.sellsnap.network

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.sirelon.sellsnap.analytics.Analytics
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume

/**
 * Hands out the token the OpenAI proxy verifies. Which provider backs it (Play Integrity in
 * release, the debug provider in debug builds) is decided by `androidApp`, which installs the
 * factory in `MainActivity` before any token is requested.
 */
internal class FirebaseAppCheckTokenProvider(
    context: Context,
    private val analytics: Analytics,
) : AppCheckTokenProvider {

    init {
        runCatching { FirebaseApp.initializeApp(context) }
    }

    override suspend fun token(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseAppCheck.getInstance()
            .getAppCheckToken(false)
            .addOnSuccessListener { continuation.resume(it.token) }
            .addOnFailureListener { error ->
                // The proxy answers 401 without a token, so the failure surfaces as a generation
                // error. Recorded so devices without Play services show up in Crashlytics.
                analytics.recordException(error, "App Check token request failed")
                continuation.resume(null)
            }
    }
}

actual val openAIEndpointModule: Module = module {
    single {
        OpenAIEndpoint(
            baseUrl = OPENAI_PROXY_BASE_URL,
            bearerToken = "",
            appCheck = FirebaseAppCheckTokenProvider(context = get(), analytics = get()),
        )
    }
}
