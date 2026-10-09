package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.announcements.model.Announcement
import org.koin.core.module.Module
import org.koin.dsl.module

// Web has no Firebase config wired up (see PhotoUploaderModule.jsWasm.kt), and GitLive's
// Firestore module doesn't publish a wasmJs target either, so Web never shows announcements.
internal class NoOpAnnouncementsRepository : AnnouncementsRepository {
    override suspend fun getAnnouncements(): List<Announcement> = emptyList()

    override suspend fun getInstallationId(): String? = null
}

actual val announcementsDataModule: Module = module {
    single<AnnouncementsRepository> { NoOpAnnouncementsRepository() }
}
