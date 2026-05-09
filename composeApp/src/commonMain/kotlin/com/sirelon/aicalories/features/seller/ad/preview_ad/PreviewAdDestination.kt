package com.sirelon.sellsnap.features.seller.ad.preview_ad

import kotlinx.serialization.Serializable

internal sealed interface PreviewAdDestination {
    @Serializable
    data object Content : PreviewAdDestination

    @Serializable
    data object BackInfo : PreviewAdDestination

    @Serializable
    data object PublishConfirm : PreviewAdDestination

    @Serializable
    data object Publishing : PreviewAdDestination
}
