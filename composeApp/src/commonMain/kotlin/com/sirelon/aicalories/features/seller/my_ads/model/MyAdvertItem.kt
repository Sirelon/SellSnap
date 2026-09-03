package com.sirelon.sellsnap.features.seller.my_ads.model

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

data class MyAdvertItem(
    val id: Long,
    val title: String,
    val status: AdvertStatus,
    val url: String,
    val primaryImageUrl: String?,
    val priceFormatted: String,
    /** Raw asking price, for pre-filling the sold price and the price edit. Null when OLX sent none. */
    val priceValue: Long?,
    val currencyCode: String,
    val createdAt: String,
    val validTo: String,
)
