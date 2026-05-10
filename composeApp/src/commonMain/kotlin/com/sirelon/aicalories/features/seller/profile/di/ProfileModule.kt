package com.sirelon.sellsnap.features.seller.profile.di

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
    singleOf(::SellerAccountRepository)
    viewModelOf(::ProfileViewModel)
}
