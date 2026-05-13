package com.sirelon.sellsnap.features.seller.ad.preview_ad

import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import kotlinx.serialization.Serializable

@Serializable
data class PreviewAdSavedState(
    val title: String? = null,
    val description: String? = null,
    val price: Float? = null,
    val selectedCategoryId: Int? = null,
    val attributeValues: Map<String, List<OlxAttributeValue>> = emptyMap(),
    val location: OlxLocation? = null,
    val publishSuccessData: PublishSuccessData? = null,
)
