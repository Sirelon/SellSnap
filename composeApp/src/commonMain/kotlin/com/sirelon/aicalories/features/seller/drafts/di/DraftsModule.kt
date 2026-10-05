package com.sirelon.sellsnap.features.seller.drafts.di

import com.sirelon.sellsnap.features.seller.drafts.DraftsRepository
import com.sirelon.sellsnap.features.seller.drafts.data.createDraftsRepository
import com.sirelon.sellsnap.features.seller.drafts.presentation.DraftsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val draftsModule = module {
    single<DraftsRepository> { createDraftsRepository(json = get()) }
    viewModelOf(::DraftsViewModel)
}
