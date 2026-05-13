package com.sirelon.sellsnap.features.seller.ad

import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import kotlinx.serialization.Serializable

sealed interface AdDestination {

    @Serializable
    data object GenerateAd : AdDestination

    @Serializable
    data object WhisperDemo : AdDestination

    @Serializable
    data object MyAdverts : AdDestination

    @Serializable
    data class PreviewAd(val advertisement: AdvertisementWithAttributes) : AdDestination

    @Serializable
    data object SelectCategory : AdDestination

    @Serializable
    data class Profile(val reason: String? = null) : AdDestination

    @Serializable
    data class ProfileAuth(val url: String) : AdDestination


    @Serializable
    data class SellerPublishSuccess(
        val data: PublishSuccessData,
    ) : AdDestination

    @Serializable
    data class ImagesPreview(val images: List<String>, val initialPage: Int) : AdDestination
}
