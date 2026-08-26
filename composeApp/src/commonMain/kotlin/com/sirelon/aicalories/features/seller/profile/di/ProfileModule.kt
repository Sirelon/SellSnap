package com.sirelon.sellsnap.features.seller.profile.di

import com.sirelon.sellsnap.features.seller.auth.di.olxAuthorizedHttpClientQualifier
import com.sirelon.sellsnap.features.seller.auth.di.olxHttpClientQualifier
import com.sirelon.sellsnap.features.seller.auth.di.olxUnauthenticatedApiClientQualifier
import com.sirelon.sellsnap.features.seller.location.createLocationProvider
import com.sirelon.sellsnap.features.seller.location.data.LocationStore
import com.sirelon.sellsnap.features.seller.location.data.LocationRepository
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import com.sirelon.sellsnap.features.seller.profile.presentation.ProfileViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val profileModule = module {
    single { createLocationProvider() }
    single { LocationStore(get()) }
    singleOf(::LocationRepository)
    single {
        SellerAccountRepository(
            authRepository = get(),
            olxApiClient = get(),
            unauthenticatedOlxApiClient = get(olxUnauthenticatedApiClientQualifier),
            authorizedHttpClient = get(olxAuthorizedHttpClientQualifier),
            unauthenticatedHttpClient = get(olxHttpClientQualifier),
            accountStore = get(),
            locationRepository = get(),
            olxCountryStore = get(),
            draftMediaFileStore = get(),
            analyticsConsentRepository = get(),
            errorParser = get(),
            analytics = get(),
        )
    }
    viewModelOf(::ProfileViewModel)
}
