package com.sirelon.aicalories.features.seller.auth.data

import com.sirelon.aicalories.features.seller.ad.publish_success.AdvertStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PostAdvertRequest(
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("advertiser_type") val advertiserType: String,
    @SerialName("contact") val contact: AdvertContactRequest,
    @SerialName("location") val location: AdvertLocationRequest,
    @SerialName("images") val images: List<AdvertImageRequest>,
    @SerialName("price") val price: AdvertPriceRequest?,
    @SerialName("attributes") val attributes: List<AdvertAttributeRequest>,
)

@Serializable
internal data class AdvertAttributeRequest(
    @SerialName("code") val code: String,
    @SerialName("values") val values: List<String>,
)

@Serializable
internal data class AdvertContactRequest(
    @SerialName("name") val name: String,
    @SerialName("phone") val phone: String?,
)

@Serializable
internal data class AdvertLocationRequest(
    @SerialName("city_id") val cityId: Int,
    @SerialName("district_id") val districtId: Int?,
)

@Serializable
internal data class AdvertImageRequest(
    @SerialName("url") val url: String,
)

@Serializable
internal data class AdvertPriceRequest(
    @SerialName("value") val value: Int,
    @SerialName("currency") val currency: String,
    @SerialName("negotiable") val negotiable: Boolean,
)

internal data class PostAdvertResult(
    val id: Long,
    val status: AdvertStatus,
    val url: String?,
)
