package com.sirelon.sellsnap.features.seller.auth.di

import com.sirelon.sellsnap.features.seller.auth.data.BuildConfigOlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.DefaultOlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.OlxTokenStore
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerAuthViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val olxHttpClientQualifier = named("olxHttpClient")
val olxAuthorizedHttpClientQualifier = named("olxAuthorizedHttpClient")

val sellerAuthModule = module {
    single<HttpClient>(qualifier = olxHttpClientQualifier) { createOlxHttpClient() }
    single { OlxRemoteErrorParser(get()) }
    single<HttpClient>(qualifier = olxAuthorizedHttpClientQualifier) {
        createOlxAuthorizedHttpClient(
            authRefreshClient = get(olxHttpClientQualifier),
            credentialsProvider = get(),
            tokenStore = get(),
            authSessionStore = get(),
            errorParser = get(),
        )
    }
    single { BuildConfigOlxCredentialsProvider() } bind OlxCredentialsProvider::class
    single { OlxTokenStore(get()) }
    single { OlxAuthSessionStore(get()) }
    single { GuestModeStore() }
    single { DefaultOlxRedirectHandler() } bind OlxRedirectHandler::class
    single {
        OlxAuthRepository(
            httpClient = get(olxHttpClientQualifier),
            credentialsProvider = get(),
            tokenStore = get(),
            authSessionStore = get(),
            redirectHandler = get(),
            guestModeStore = get(),
            errorParser = get(),
        )
    }
    single { OlxApiClient(httpClient = get(olxAuthorizedHttpClientQualifier), json = get(), errorParser = get()) }
    viewModelOf(::SellerAuthViewModel)
}
