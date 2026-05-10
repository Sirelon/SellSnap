package com.sirelon.sellsnap.features.seller.ad

import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import kotlinx.serialization.Serializable

@Serializable
data class AdvertisementWithAttributes(
    val advertisement: Advertisement,
    val filledAttributes: Map<String, List<OlxAttributeValue>>,
)