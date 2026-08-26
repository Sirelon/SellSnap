package com.sirelon.sellsnap.features.whatsnew.di

import com.sirelon.sellsnap.features.whatsnew.data.WhatsNewStore
import com.sirelon.sellsnap.features.whatsnew.data.releaseNotesModule
import com.sirelon.sellsnap.features.whatsnew.presentation.WhatsNewViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val whatsNewModule = module {
    includes(releaseNotesModule)
    singleOf(::WhatsNewStore)
    viewModelOf(::WhatsNewViewModel)
}
