package com.sirelon.sellsnap.features.whatsnew.data

import com.sirelon.sellsnap.features.whatsnew.model.Release
import org.koin.core.module.Module
import org.koin.dsl.module

// Web has no Firebase config wired up (see PhotoUploaderModule.jsWasm.kt), and GitLive's
// Firestore module doesn't publish a wasmJs target either, so Web never shows What's New.
internal class NoOpReleaseNotesRepository : ReleaseNotesRepository {
    override suspend fun getReleases(): List<Release> = emptyList()
}

actual val releaseNotesModule: Module = module {
    single<ReleaseNotesRepository> { NoOpReleaseNotesRepository() }
}
