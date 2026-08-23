package com.sirelon.sellsnap.features.seller.settings.di

import com.sirelon.sellsnap.features.seller.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    viewModelOf(::SettingsViewModel)
}
