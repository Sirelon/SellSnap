package com.sirelon.sellsnap.features.notifications.di

import com.sirelon.sellsnap.features.notifications.data.NotificationsStore
import com.sirelon.sellsnap.features.notifications.data.PushNotificationsRepository
import com.sirelon.sellsnap.features.notifications.data.pushNotificationsPlatformModule
import com.sirelon.sellsnap.features.notifications.presentation.NotificationsPromptViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val notificationsModule = module {
    includes(pushNotificationsPlatformModule)
    // Not singleOf(::NotificationsStore): the class has a second, injectable constructor for tests,
    // which makes the callable reference ambiguous.
    single { NotificationsStore() }
    single {
        PushNotificationsRepository(
            platform = get(),
            store = get(),
            analytics = get(),
        )
    }
    viewModelOf(::NotificationsPromptViewModel)
}
