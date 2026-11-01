package com.sirelon.sellsnap.features.seller.messages.di

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.di.olxUnauthenticatedApiClientQualifier
import com.sirelon.sellsnap.features.seller.messages.data.MessagesRepository
import com.sirelon.sellsnap.features.seller.messages.presentation.ConversationViewModel
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesBadgeViewModel
import com.sirelon.sellsnap.features.seller.messages.presentation.MessagesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val messagesModule = module {
    single {
        MessagesRepository(
            accountRepository = get(),
            unauthenticatedOlxApiClient = get(olxUnauthenticatedApiClientQualifier),
        )
    }
    viewModel {
        MessagesViewModel(
            repository = get(),
            myAdvertsRepository = get(),
            accountRepository = get(),
            analytics = get(),
        )
    }
    // Six parameters, past what Koin can destructure: read them by position, in the order the
    // conversation route passes them.
    viewModel { params ->
        ConversationViewModel(
            threadId = params.get(0),
            advertId = params.get(1),
            title = params.get(2),
            imageUrl = params.get(3),
            status = params.get(4),
            isListingDeleted = params.get(5),
            repository = get(),
            myAdvertsRepository = get(),
            accountRepository = get(),
            analytics = get(),
        )
    }
    viewModel { MessagesBadgeViewModel(repository = get(), accountRepository = get()) }
}
