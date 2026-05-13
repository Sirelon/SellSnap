package com.sirelon.sellsnap.features.seller.auth.data.response

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvert
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertPrice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class OlxAdvertsRootResponse(
    @SerialName("data")
    val data: List<OlxAdvertResponse>?,
)

@Serializable
internal class OlxAdvertResponse(
    @SerialName("id")
    val id: Long?,

    @SerialName("status")
    val status: String?,

    @SerialName("url")
    val url: String?,

    @SerialName("created_at")
    val createdAt: String?,

    @SerialName("valid_to")
    val validTo: String?,

    @SerialName("title")
    val title: String?,

    @SerialName("images")
    val images: List<OlxAdvertImageResponse>?,

    @SerialName("price")
    val price: OlxAdvertPriceResponse?,
) {
    fun toDomain(): OlxAdvert? {
        val advertId = id ?: return null
        return OlxAdvert(
            id = advertId,
            title = title.orEmpty(),
            status = AdvertStatus.from(status.orEmpty()),
            url = url.orEmpty(),
            primaryImageUrl = images
                .orEmpty()
                .firstNotNullOfOrNull { it.url?.takeIf(String::isNotBlank) },
            price = price?.toDomain(),
            createdAt = createdAt.orEmpty(),
            validTo = validTo.orEmpty(),
        )
    }
}

@Serializable
internal class OlxAdvertImageResponse(
    @SerialName("url")
    val url: String?,
)

@Serializable
internal class OlxAdvertPriceResponse(
    @SerialName("value")
    val value: Long?,

    @SerialName("currency")
    val currency: String?,

    @SerialName("negotiable")
    val negotiable: Boolean?,
) {
    fun toDomain(): OlxAdvertPrice? {
        val priceValue = value ?: return null
        return OlxAdvertPrice(
            value = priceValue,
            currency = currency.orEmpty(),
            negotiable = negotiable ?: false,
        )
    }
}
