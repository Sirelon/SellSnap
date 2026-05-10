package com.sirelon.aicalories.di

import com.sirelon.aicalories.Greeting
import com.sirelon.aicalories.features.media.di.mediaModule
import com.sirelon.aicalories.features.seller.ad.generate_ad.di.generateAdModule
import com.sirelon.aicalories.features.seller.ad.preview_ad.di.previewAdModule
import com.sirelon.aicalories.features.seller.auth.di.sellerAuthModule
import com.sirelon.aicalories.features.seller.categories.categoriesModule
import com.sirelon.aicalories.features.seller.openai.OpenAIClient
import com.sirelon.aicalories.features.seller.profile.di.profileModule
import com.sirelon.aicalories.features.seller.whisper.di.whisperModule
import com.sirelon.aicalories.network.ApiTokenProvider
import com.sirelon.aicalories.network.createHttpClient
import com.sirelon.aicalories.network.createOpenAI
import com.sirelon.aicalories.startup.appStartupModule
import com.sirelon.aicalories.supabase.SupabaseClient
import com.sirelon.aicalories.supabase.SupabaseConfig
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
        whisperModule,
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
