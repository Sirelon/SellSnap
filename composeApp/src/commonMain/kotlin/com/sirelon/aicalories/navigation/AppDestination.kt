package com.sirelon.sellsnap.navigation

import androidx.navigation3.runtime.NavKey
import com.sirelon.sellsnap.analytics.AnalyticsScreen
import kotlinx.serialization.Serializable

/**
 * High level destinations rendered by Navigation3.
 */
@Serializable
sealed interface AppDestination : NavKey, AnalyticsScreen {

    @Serializable
    data object Splash : AppDestination {
        override val screenName = "Splash"
    }

    @Serializable
    data object SellerOnboarding : AppDestination {
        override val screenName = "SellerOnboarding"
    }

    @Serializable
    data object ConsentPrompt : AppDestination {
        override val screenName = "ConsentPrompt"
    }

    @Serializable
    data object SellerLanding : AppDestination {
        override val screenName = "SellerLanding"
    }

    @Serializable
    data object Seller : AppDestination {
        override val screenName = "Seller"
    }

    @Serializable
    data object DeleteAccountDataConfirm : AppDestination {
        override val screenName = "DeleteAccountDataConfirm"
    }

    @Serializable
    data object OlxCountryPicker : AppDestination {
        override val screenName = "OlxCountryPicker"
    }
}
