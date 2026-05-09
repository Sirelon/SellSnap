package com.sirelon.sellsnap.di

import com.sirelon.sellsnap.Greeting
import com.sirelon.sellsnap.features.media.di.mediaModule
import com.sirelon.sellsnap.features.seller.ad.generate_ad.di.generateAdModule
import com.sirelon.sellsnap.features.seller.ad.preview_ad.di.previewAdModule
import com.sirelon.sellsnap.features.seller.auth.di.sellerAuthModule
import com.sirelon.sellsnap.features.seller.categories.categoriesModule
import com.sirelon.sellsnap.features.seller.openai.OpenAIClient
import com.sirelon.sellsnap.features.seller.profile.di.profileModule
import com.sirelon.sellsnap.network.ApiTokenProvider
import com.sirelon.sellsnap.network.createHttpClient
import com.sirelon.sellsnap.network.createOpenAI
import com.sirelon.sellsnap.startup.appStartupModule
import com.sirelon.sellsnap.supabase.SupabaseClient
import com.sirelon.sellsnap.supabase.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val applicationScopeQualifier = named("applicationScope")

val appModule = module {
    includes(
        mediaModule,
        sellerAuthModule,
        generateAdModule,
        previewAdModule,
        profileModule,
        appStartupModule,
        categoriesModule,
    )
    single { Greeting() }
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

val networkModule = module {
    single {
        ApiTokenProvider()
            .apply { token = SupabaseConfig.OPENAI_KEY }
    }
    single { createHttpClient(get()) }
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
        }
    }
    single { createOpenAI(get()) }
    single { OpenAIClient(openAI = get(), json = get(), compactJson = get()) }
    singleOf(::SupabaseClient)
}
