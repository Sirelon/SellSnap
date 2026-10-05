package com.sirelon.sellsnap.features.seller.ad.preview_ad.di

import com.sirelon.sellsnap.di.applicationScopeQualifier
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes
import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val previewAdModule = module {
    // Spelled out rather than viewModelOf: the application scope is a qualified binding, which
    // constructor reflection cannot pick.
    viewModel { (listing: AdvertisementWithAttributes) ->
        PreviewAdViewModel(
            filledAdvertisement = listing,
            categoriesRepository = get(),
            locationRepository = get(),
            olxApiClient = get(),
            currencyRepository = get(),
            attributeValidator = get(),
            authRepository = get(),
            accountRepository = get(),
            olxCountryStore = get(),
            adFlowTimerStore = get(),
            savedStateHandle = get(),
            json = get(),
            analytics = get(),
            openAiClient = get(),
            adGenerationLogRepository = get(),
            advertOutcomeStore = get(),
            reviewPromptCoordinator = get(),
            draftsRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
}
