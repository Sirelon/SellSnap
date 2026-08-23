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
    data object ConsentPrompt : AppDestination

    @Serializable
    data object SellerLanding : AppDestination

    @Serializable
    data object Seller : AppDestination

    @Serializable
    data object DeleteAccountDataConfirm : AppDestination

    @Serializable
    data object OlxCountryPicker : AppDestination

    // SIR-83: multi-account bottom sheets, wired next to DeleteAccountDataConfirm above. These
    // stay at the app level (rather than as AdDestination entries in features/seller/ad/**)
    // because a sibling task owns that package for the Publish/Preview and My Ads work.
    @Serializable
    data object AddOlxAccountConfirm : AppDestination

    @Serializable
    data object OlxAccountAuthFailed : AppDestination

    @Serializable
    data class DisconnectOlxAccountConfirm(val localIndex: Int) : AppDestination
}
