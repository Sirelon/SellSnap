package com.sirelon.sellsnap.features.review.di

import com.sirelon.sellsnap.features.review.ReviewPromptCoordinator
import com.sirelon.sellsnap.features.review.ReviewPromptStore
import org.koin.dsl.module

val reviewPromptModule = module {
    // Not singleOf(::ReviewPromptStore): the class has a second, injectable constructor for tests,
    // which makes the callable reference ambiguous.
    single { ReviewPromptStore() }
    single { ReviewPromptCoordinator(store = get(), analytics = get()) }
}
