package com.sirelon.sellsnap.features.seller.auth.di

import com.sirelon.sellsnap.features.seller.auth.data.BuildConfigOlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.DefaultOlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.GuestModeStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountMigration
import com.sirelon.sellsnap.features.seller.auth.data.OlxAccountStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthRepository
import com.sirelon.sellsnap.features.seller.auth.data.OlxAuthSessionStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCredentialsProvider
import com.sirelon.sellsnap.features.seller.auth.data.OlxRedirectHandler
import com.sirelon.sellsnap.features.seller.auth.data.OlxRemoteErrorParser
import com.sirelon.sellsnap.features.seller.auth.data.OlxTokenStore
import com.sirelon.sellsnap.features.seller.auth.data.OlxCountryStore
import com.sirelon.sellsnap.features.seller.auth.data.createOlxAuthorizedHttpClient
import com.sirelon.sellsnap.features.seller.auth.data.createOlxHttpClient
import com.sirelon.sellsnap.features.seller.auth.presentation.SellerAuthViewModel
import com.sirelon.sellsnap.features.seller.currency.data.CurrencyRepository
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val olxHttpClientQualifier = named("olxHttpClient")
val olxAuthorizedHttpClientQualifier = named("olxAuthorizedHttpClient")
val olxUnauthenticatedApiClientQualifier = named("olxUnauthenticatedApiClient")

val sellerAuthModule = module {
    single { OlxCountryStore(get()) }
    single<HttpClient>(qualifier = olxHttpClientQualifier) { createOlxHttpClient() }
    single { OlxRemoteErrorParser(get()) }
    single<HttpClient>(qualifier = olxAuthorizedHttpClientQualifier) {
        createOlxAuthorizedHttpClient(
            authRefreshClient = get(olxHttpClientQualifier),
            credentialsProvider = get(),
            accountStore = get(),
            countryStore = get(),
            errorParser = get(),
        )
    }
    single { BuildConfigOlxCredentialsProvider() } bind OlxCredentialsProvider::class
    single { OlxTokenStore(get()) }
    single { OlxAccountStore(get()) }
    single { OlxAccountMigration(accountStore = get(), legacyTokenStore = get(), countryStore = get()) }
    single { OlxAuthSessionStore(get()) }
    single { GuestModeStore() }
    single { DefaultOlxRedirectHandler() } bind OlxRedirectHandler::class
    single {
        OlxAuthRepository(
            httpClient = get(olxHttpClientQualifier),
            credentialsProvider = get(),
            accountStore = get(),
            countryStore = get(),
            authSessionStore = get(),
            redirectHandler = get(),
            guestModeStore = get(),
            errorParser = get(),
        )
    }
    single { OlxApiClient(httpClient = get(olxAuthorizedHttpClientQualifier), json = get(), errorParser = get()) }
    // Used only for the add-account/reconnect users/me dedupe check (SIR-83): the freshly
    // exchanged token is deliberately not in the account store yet, so it can't go through the
    // authorized client's account-store-backed bearer provider.
    single<OlxApiClient>(qualifier = olxUnauthenticatedApiClientQualifier) {
        OlxApiClient(httpClient = get(olxHttpClientQualifier), json = get(), errorParser = get())
    }
    single { CurrencyRepository(olxApiClient = get()) }
    viewModelOf(::SellerAuthViewModel)
}
