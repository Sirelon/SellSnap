package com.sirelon.sellsnap.features.seller.ad

import kotlinx.serialization.Serializable

@Serializable
data class Advertisement(
    val title: String,
    val description: String,
    val images: List<String>,
    val suggestedPrice: Float,
    val minPrice: Float,
    val maxPrice: Float,
)