package com.sirelon.sellsnap.features.announcements.data

import com.sirelon.sellsnap.features.announcements.model.Announcement
import org.koin.core.module.Module
import org.koin.dsl.module

// Desktop never initializes a Firebase app in this repo (see PhotoUploaderModule.jvm.kt), so
// announcements are unavailable here even though GitLive's Firestore module ships a JVM target.
internal class NoOpAnnouncementsRepository : AnnouncementsRepository {
    override suspend fun getAnnouncements(): List<Announcement> = emptyList()

    override suspend fun getInstallationId(): String? = null
}

actual val announcementsDataModule: Module = module {
    single<AnnouncementsRepository> { NoOpAnnouncementsRepository() }
}
