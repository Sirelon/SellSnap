package com.sirelon.sellsnap.features.seller.auth.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

data class OlxAdvert(
    val id: Long,
    val title: String,
    val status: AdvertStatus,
    val url: String,
    val primaryImageUrl: String?,
    val price: OlxAdvertPrice?,
    val createdAt: String,
    val validTo: String,
)

data class OlxAdvertPrice(
    val value: Long,
    val currency: String,
    val negotiable: Boolean,
)
