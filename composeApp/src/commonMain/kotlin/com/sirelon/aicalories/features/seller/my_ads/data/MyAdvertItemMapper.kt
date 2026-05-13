package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvert
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertPrice
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem

internal object MyAdvertItemMapper {
    fun map(advert: OlxAdvert): MyAdvertItem = MyAdvertItem(
        id = advert.id,
        title = advert.title,
        status = advert.status,
        url = advert.url,
        primaryImageUrl = advert.primaryImageUrl,
        priceFormatted = advert.price?.format().orEmpty(),
        createdAt = advert.createdAt,
        validTo = advert.validTo,
    )

    private fun OlxAdvertPrice.format(): String {
        val amount = value
            .coerceAtLeast(0L)
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()

        return when (val code = currency.uppercase()) {
            "UAH" -> "₴ $amount"
            "" -> amount
            else -> "$amount $code"
        }
    }
}
