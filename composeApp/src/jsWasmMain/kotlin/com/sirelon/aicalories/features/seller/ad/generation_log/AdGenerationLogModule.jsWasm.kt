package com.sirelon.sellsnap.features.seller.ad.generation_log

import org.koin.core.module.Module
import org.koin.dsl.module

// GitLive's Firebase Firestore KMP library ships no Wasm target (and Web has no Firebase config wired up).
actual val adGenerationLogModule: Module = module {
    single<AdGenerationLogRepository> { NoOpAdGenerationLogRepository }
}
