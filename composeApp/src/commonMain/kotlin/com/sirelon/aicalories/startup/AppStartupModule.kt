package com.sirelon.sellsnap.startup

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appStartupModule = module {
    singleOf(::AppStartupStore)
    single { AppThemePreferenceStore() }
    single {
        AppThemeRepository(
            store = get(),
            applicationScope = get(named("applicationScope")),
        )
    }
    viewModelOf(::AppNavigationViewModel)
}
