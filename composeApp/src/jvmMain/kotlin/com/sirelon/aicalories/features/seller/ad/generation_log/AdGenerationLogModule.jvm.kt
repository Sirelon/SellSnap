package com.sirelon.sellsnap.features.seller.ad.generation_log

import org.koin.core.module.Module
import org.koin.dsl.module

// Desktop has no Firebase configuration wired up, so ad-generation logging stays off there.
actual val adGenerationLogModule: Module = module {
    single<AdGenerationLogRepository> { NoOpAdGenerationLogRepository }
}
