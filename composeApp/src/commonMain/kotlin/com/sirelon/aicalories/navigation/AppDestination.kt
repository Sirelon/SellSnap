package com.sirelon.sellsnap.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * High level destinations rendered by Navigation3.
 */
@Serializable
sealed interface AppDestination : NavKey {

    @Serializable
    data object Splash : AppDestination

    @Serializable
    data object SellerOnboarding : AppDestination

    @Serializable
    data object SellerLanding : AppDestination

    @Serializable
    data object Seller : AppDestination
}
