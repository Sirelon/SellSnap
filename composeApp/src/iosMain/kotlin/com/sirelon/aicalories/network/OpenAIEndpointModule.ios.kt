package com.sirelon.sellsnap.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * Implemented in Swift (`iOSApp.swift`): FirebaseAppCheck is only reachable from the Xcode side.
 * Registered on [AppCheckBridge] right after `FirebaseApp.configure()`.
 */
interface AppCheckTokenFetcher {
    fun fetch(onResult: (String?) -> Unit)
}

object AppCheckBridge {
    var fetcher: AppCheckTokenFetcher? = null
}

private val APP_CHECK_TIMEOUT = 15.seconds

internal class IosAppCheckTokenProvider : AppCheckTokenProvider {
    // The Swift fetcher has no force-refresh path (`AppCheck.token(forcingRefresh:)` would need
    // a second bridge method), so a 401 retry on iOS re-sends the cached token and is skipped by
    // the plugin. Android, where the stale-token failures were seen, does refresh.
    override suspend fun token(forceRefresh: Boolean): String? {
        val fetcher = AppCheckBridge.fetcher ?: return null
        return withTimeoutOrNull(APP_CHECK_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                fetcher.fetch { token ->
                    if (continuation.isActive) continuation.resume(token)
                }
            }
        }
    }
}

actual val openAIEndpointModule: Module = module {
    single {
        OpenAIEndpoint(
            baseUrl = OPENAI_PROXY_BASE_URL,
            bearerToken = "",
            appCheck = IosAppCheckTokenProvider(),
        )
    }
}
