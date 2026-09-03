package com.sirelon.sellsnap.features.seller.auth.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

/** One advert as returned by `GET adverts/{id}`, which carries the fields the list call omits. */
data class OlxAdvertDetail(
    val id: Long,
    val title: String,
    val description: String,
    val status: AdvertStatus,
    val url: String,
    val categoryId: Int?,
    val price: OlxAdvertPrice?,
    val imageUrls: List<String>,
    val createdAt: String,
    val validTo: String,
    val autoExtendEnabled: Boolean,
)
