package com.sirelon.sellsnap.features.media.upload

import org.koin.core.module.Module
import org.koin.dsl.module

// GitLive's Firebase Storage KMP library ships no Wasm target (and Web has no Firebase config wired
// up), so Web keeps using Supabase.
actual val photoUploaderModule: Module = module {
    single<PhotoUploader> { get<SupabasePhotoUploader>() }
}
