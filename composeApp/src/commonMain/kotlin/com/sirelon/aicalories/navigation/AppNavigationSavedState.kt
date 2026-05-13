package com.sirelon.sellsnap.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val appNavigationSavedStateConfiguration: SavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(AppDestination.Splash::class, AppDestination.Splash.serializer())
                subclass(AppDestination.SellerOnboarding::class, AppDestination.SellerOnboarding.serializer())
                subclass(AppDestination.SellerLanding::class, AppDestination.SellerLanding.serializer())
                subclass(AppDestination.Seller::class, AppDestination.Seller.serializer())
            }
        }
    }
