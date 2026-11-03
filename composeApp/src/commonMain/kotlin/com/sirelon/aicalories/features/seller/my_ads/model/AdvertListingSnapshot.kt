package com.sirelon.sellsnap.features.seller.my_ads.model

/**
 * One advert as the conversation reads it: the row it shows, plus the listing's own text for AI
 * reply suggestions (SIR-96). [attributes] are OLX's raw attribute codes and values as `GET
 * adverts/{id}` returns them; their labels are not fetched.
 */
data class AdvertListingSnapshot(
    val item: MyAdvertItem,
    val description: String,
    val attributes: List<ListingAttribute>,
)

/** An attribute of a published advert, e.g. `state` = [`used`]. */
data class ListingAttribute(
    val code: String,
    val values: List<String>,
)
