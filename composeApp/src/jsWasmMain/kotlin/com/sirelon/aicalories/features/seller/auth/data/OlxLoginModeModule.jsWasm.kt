package com.sirelon.sellsnap.features.seller.auth.data

import org.koin.core.module.Module
import org.koin.dsl.module

// The web build always opens a browser tab; the switch only exists on Android and iOS.
actual val olxLoginModeModule: Module = module {
    single<OlxLoginModeRepository> {
        object : OlxLoginModeRepository {
            override suspend fun useSystemBrowser(): Boolean = true
        }
    }
}
