package com.sirelon.sellsnap.features.seller.ad

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val adNavigationSavedStateConfiguration: SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(AdDestination.GenerateAd::class, AdDestination.GenerateAd.serializer())
                subclass(AdDestination.WhisperDemo::class, AdDestination.WhisperDemo.serializer())
                subclass(AdDestination.PreviewAd::class, AdDestination.PreviewAd.serializer())
                subclass(AdDestination.SelectCategory::class, AdDestination.SelectCategory.serializer())
                subclass(AdDestination.Profile::class, AdDestination.Profile.serializer())
                subclass(AdDestination.ProfileAuth::class, AdDestination.ProfileAuth.serializer())
                subclass(AdDestination.SellerPublishSuccess::class, AdDestination.SellerPublishSuccess.serializer())
                subclass(AdDestination.ImagesPreview::class, AdDestination.ImagesPreview.serializer())
            }
        }
    }
