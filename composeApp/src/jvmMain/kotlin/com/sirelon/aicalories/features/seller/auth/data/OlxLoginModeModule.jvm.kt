package com.sirelon.sellsnap.features.seller.auth.data

import org.koin.core.module.Module
import org.koin.dsl.module

// Desktop always opens the system browser; the switch only exists on Android and iOS.
actual val olxLoginModeModule: Module = module {
    single<OlxLoginModeRepository> {
        object : OlxLoginModeRepository {
            override suspend fun useSystemBrowser(): Boolean = true
        }
    }
}
