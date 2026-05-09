package com.sirelon.sellsnap.startup

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appStartupModule = module {
    singleOf(::AppStartupStore)
    viewModelOf(::AppNavigationViewModel)
}
