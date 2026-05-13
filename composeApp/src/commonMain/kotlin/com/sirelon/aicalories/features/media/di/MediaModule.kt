package com.sirelon.sellsnap.features.media.di

import com.sirelon.sellsnap.features.media.imageFormatConverter
import com.sirelon.sellsnap.features.media.upload.createDraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.MediaUploadHelper
import com.sirelon.sellsnap.features.media.upload.MediaUploadRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mediaModule = module {
    single { imageFormatConverter() }
    single { createDraftMediaFileStore() }
    singleOf(::MediaUploadRepository)
    singleOf(::MediaUploadHelper)
}
