package com.sirelon.sellsnap.features.seller.ad

import com.sirelon.sellsnap.analytics.AnalyticsScreen
import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface AdDestination : NavKey, AnalyticsScreen {

    @Serializable
    data object GenerateAd : AdDestination {
        override val screenName = "GenerateAd"
    }

    @Serializable
    data object MyAdverts : AdDestination {
        override val screenName = "MyAdverts"
    }

    @Serializable
    data class PreviewAd(val advertisement: AdvertisementWithAttributes) : AdDestination {
        override val screenName = "PreviewAd"
    }

    @Serializable
    data object SelectCategory : AdDestination {
        override val screenName = "SelectCategory"
    }

    @Serializable
    data class Profile(val reason: String? = null) : AdDestination {
        override val screenName = "Profile"
    }

    @Serializable
    data object Settings : AdDestination {
        override val screenName = "Settings"
    }

    @Serializable
    data class SellerPublishSuccess(
        val data: PublishSuccessData,
    ) : AdDestination {
        override val screenName = "SellerPublishSuccess"
    }

    @Serializable
    data class ImagesPreview(val images: List<String>, val initialPage: Int) : AdDestination {
        override val screenName = "ImagesPreview"
    }
}
