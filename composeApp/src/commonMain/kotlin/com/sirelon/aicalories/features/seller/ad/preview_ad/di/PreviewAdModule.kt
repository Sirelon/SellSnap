package com.sirelon.sellsnap.features.seller.ad.preview_ad.di

import com.sirelon.sellsnap.features.seller.ad.preview_ad.PreviewAdViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val previewAdModule = module {
    viewModelOf(::PreviewAdViewModel)
}
