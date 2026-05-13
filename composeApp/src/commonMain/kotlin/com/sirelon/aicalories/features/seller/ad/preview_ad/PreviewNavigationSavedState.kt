package com.sirelon.sellsnap.features.seller.ad.preview_ad

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal val previewNavigationSavedStateConfiguration: SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(PreviewAdDestination.Content::class, PreviewAdDestination.Content.serializer())
                subclass(PreviewAdDestination.BackInfo::class, PreviewAdDestination.BackInfo.serializer())
                subclass(PreviewAdDestination.PublishConfirm::class, PreviewAdDestination.PublishConfirm.serializer())
                subclass(PreviewAdDestination.Publishing::class, PreviewAdDestination.Publishing.serializer())
            }
        }
    }
