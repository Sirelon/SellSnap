package com.sirelon.sellsnap.network

import org.koin.core.module.Module
import org.koin.dsl.module

// Web has no App Check provider wired up (it would need reCAPTCHA Enterprise), so the proxy
// rejects its calls with 401 and listing generation is unavailable there.
actual val openAIEndpointModule: Module = module {
    single {
        OpenAIEndpoint(
            baseUrl = OPENAI_PROXY_BASE_URL,
            bearerToken = "",
            appCheck = null,
        )
    }
}
