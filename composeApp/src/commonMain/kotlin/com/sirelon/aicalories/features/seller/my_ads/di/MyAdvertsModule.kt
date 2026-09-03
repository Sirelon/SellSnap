package com.sirelon.sellsnap.features.seller.my_ads.di

import com.sirelon.sellsnap.features.seller.auth.di.olxUnauthenticatedApiClientQualifier
import com.sirelon.sellsnap.features.seller.my_ads.data.AdvertLifecycleRepository
import com.sirelon.sellsnap.features.seller.my_ads.data.MyAdvertsRepository
import com.sirelon.sellsnap.features.seller.my_ads.presentation.MyAdvertsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val myAdvertsModule = module {
    factory {
        MyAdvertsRepository(
            accountRepository = get(),
            unauthenticatedOlxApiClient = get(olxUnauthenticatedApiClientQualifier),
        )
    }
    factory {
        AdvertLifecycleRepository(
            accountRepository = get(),
            unauthenticatedOlxApiClient = get(olxUnauthenticatedApiClientQualifier),
            myAdvertsRepository = get(),
        )
    }
    viewModel {
        MyAdvertsViewModel(
            repository = get(),
            accountRepository = get(),
            lifecycleRepository = get(),
            outcomeStore = get(),
            analytics = get(),
        )
    }
}
