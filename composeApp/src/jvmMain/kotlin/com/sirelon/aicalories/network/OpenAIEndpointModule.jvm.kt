package com.sirelon.sellsnap.network

import org.koin.core.module.Module
import org.koin.dsl.module

// Desktop has no App Check, so it cannot go through the proxy. It calls OpenAI directly with a
// key read from the environment at launch: `OPENAI_KEY=sk-... ./gradlew :composeApp:run`.
actual val openAIEndpointModule: Module = module {
    single {
        OpenAIEndpoint(
            baseUrl = OPENAI_DIRECT_BASE_URL,
            bearerToken = System.getenv("OPENAI_KEY").orEmpty(),
            appCheck = null,
        )
    }
}
