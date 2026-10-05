package com.sirelon.sellsnap.features.seller.drafts.di

import com.sirelon.sellsnap.features.seller.drafts.DraftsRepository
import com.sirelon.sellsnap.features.seller.drafts.InMemoryDraftsRepository
import org.koin.dsl.module

val draftsModule = module {
    single<DraftsRepository> { InMemoryDraftsRepository() }
}
