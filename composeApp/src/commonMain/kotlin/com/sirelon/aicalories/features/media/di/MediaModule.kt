package com.sirelon.sellsnap.features.media.di

import com.sirelon.sellsnap.features.media.imageFormatConverter
import com.sirelon.sellsnap.features.media.upload.createDraftMediaFileStore
import com.sirelon.sellsnap.features.media.upload.MediaUploadHelper
import com.sirelon.sellsnap.features.media.upload.MediaUploadRepository
import com.sirelon.sellsnap.features.media.upload.SupabasePhotoUploader
import com.sirelon.sellsnap.features.media.upload.photoUploaderModule
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mediaModule = module {
    includes(photoUploaderModule)
    single { imageFormatConverter() }
    single { createDraftMediaFileStore() }
    singleOf(::SupabasePhotoUploader)
    singleOf(::MediaUploadRepository)
    singleOf(::MediaUploadHelper)
}
