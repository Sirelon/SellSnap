package com.sirelon.sellsnap.features.seller.ad

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Advertisement(
    val title: String,
    val description: String,
    val images: List<String>,
    val suggestedPrice: Float,
    val minPrice: Float,
    val maxPrice: Float,
)