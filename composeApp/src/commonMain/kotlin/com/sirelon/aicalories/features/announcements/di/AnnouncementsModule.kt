package com.sirelon.sellsnap.features.announcements.di

import com.sirelon.sellsnap.features.announcements.data.AnnouncementsStore
import com.sirelon.sellsnap.features.announcements.data.announcementsDataModule
import com.sirelon.sellsnap.features.announcements.presentation.AnnouncementViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val announcementsModule = module {
    includes(announcementsDataModule)
    // Not singleOf(::AnnouncementsStore): the class has a second, injectable constructor for tests,
    // which makes the callable reference ambiguous.
    single { AnnouncementsStore() }
    viewModelOf(::AnnouncementViewModel)
}
