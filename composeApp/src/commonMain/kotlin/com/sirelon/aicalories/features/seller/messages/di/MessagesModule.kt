package com.sirelon.sellsnap.features.seller.messages.di

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
    viewModel { (threadId: Long, advertId: Long) ->
        ConversationViewModel(
            threadId = threadId,
            advertId = advertId,
            repository = get(),
            myAdvertsRepository = get(),
            accountRepository = get(),
            analytics = get(),
        )
    }
    viewModel { MessagesBadgeViewModel(repository = get(), accountRepository = get()) }
}
