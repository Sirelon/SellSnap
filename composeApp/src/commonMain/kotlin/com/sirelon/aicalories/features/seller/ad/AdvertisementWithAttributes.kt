package com.sirelon.sellsnap.features.seller.ad

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class AdvertisementWithAttributes(
    val advertisement: Advertisement,
    val filledAttributes: Map<String, List<OlxAttributeValue>>,
)