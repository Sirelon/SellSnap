package com.sirelon.sellsnap.features.seller.location

import kotlinx.serialization.Serializable

@Serializable
data class OlxLocation(
    val cityId: Int,
    val cityName: String,
    val districtId: Int?,
    val districtName: String?,
) {
    val displayName: String
        get() = if (districtName != null) "$cityName, $districtName" else cityName
}
