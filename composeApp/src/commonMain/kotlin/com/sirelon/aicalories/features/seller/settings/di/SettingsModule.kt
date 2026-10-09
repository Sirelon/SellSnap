package com.sirelon.sellsnap.features.seller.settings.di

import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    // Not viewModelOf(::SettingsViewModel): the defaulted isAndroid parameter would be looked up in Koin.
    viewModel {
        SettingsViewModel(
            themeRepository = get(),
            analyticsConsentRepository = get(),
            notificationsRepository = get(),
        )
    }
}
