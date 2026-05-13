package com.sirelon.sellsnap.features.seller.my_ads.model

import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatus

data class MyAdvertItem(
    val id: Long,
    val title: String,
    val status: OlxAdvertStatus,
    val url: String,
    val primaryImageUrl: String?,
    val priceFormatted: String,
    val createdAt: String,
    val validTo: String,
) {
    val canOpen: Boolean
        get() = url.isNotBlank()
}
