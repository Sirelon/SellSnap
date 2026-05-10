package com.sirelon.sellsnap.features.seller.ad.publish_success

import kotlinx.serialization.Serializable

@Serializable
enum class AdvertStatus {
    New, Limited, Unknown;

    companion object {
        fun from(value: String): AdvertStatus = when (value) {
            "new" -> New
            "limited" -> Limited
            else -> Unknown
        }
    }
}

@Serializable
data class PublishSuccessData(
    val url: String,
    val title: String,
    val priceFormatted: String,
    val primaryImageUrl: String?,
    val totalElapsedMs: Long,
    val status: AdvertStatus = AdvertStatus.Unknown,
)
