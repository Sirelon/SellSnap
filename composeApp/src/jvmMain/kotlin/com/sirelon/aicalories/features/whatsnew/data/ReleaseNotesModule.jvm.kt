package com.sirelon.sellsnap.features.whatsnew.data

import com.sirelon.sellsnap.features.whatsnew.model.Release
import org.koin.core.module.Module
import org.koin.dsl.module

// Desktop never initializes a Firebase app in this repo (see PhotoUploaderModule.jvm.kt), so
// What's New is unavailable here even though GitLive's Firestore module does ship a JVM target.
internal class NoOpReleaseNotesRepository : ReleaseNotesRepository {
    override suspend fun getReleases(): List<Release> = emptyList()
}

actual val releaseNotesModule: Module = module {
    single<ReleaseNotesRepository> { NoOpReleaseNotesRepository() }
}
