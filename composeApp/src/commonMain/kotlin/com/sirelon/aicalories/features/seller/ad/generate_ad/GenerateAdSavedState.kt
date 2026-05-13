package com.sirelon.sellsnap.features.seller.ad.generate_ad

import com.sirelon.sellsnap.features.media.upload.DraftPhoto
import kotlinx.serialization.Serializable

@Serializable
data class GenerateAdSavedState(
    val prompt: String = "",
    val photos: List<DraftPhoto> = emptyList(),
)
