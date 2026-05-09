package com.sirelon.sellsnap.features.seller.ad.generate_ad.di

import com.sirelon.sellsnap.features.seller.ad.AdFlowTimerStore
import com.sirelon.sellsnap.features.seller.ad.generate_ad.GenerateAdViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val generateAdModule = module {
    singleOf(::AdFlowTimerStore)
    viewModelOf(::GenerateAdViewModel)
}
