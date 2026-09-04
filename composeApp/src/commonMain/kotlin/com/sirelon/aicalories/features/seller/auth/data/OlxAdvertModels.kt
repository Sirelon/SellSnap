package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertDetail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

/**
 * One advert read back for editing (SIR-104). [updatePayload] is the raw `data` object from
 * `GET adverts/{id}` minus the keys `PUT` does not accept, so an edit re-sends every field the
 * seller did not touch byte-for-byte as OLX returned it. That is what keeps a price change from
 * silently wiping attributes, delivery settings, or any field this app does not model.
 */
internal data class AdvertEditSnapshot(
    val detail: OlxAdvertDetail,
    val updatePayload: JsonObject,
)

/**
 * Body of `POST adverts/{id}/commands`. [isSuccess] is required by OLX for
 * [AdvertCommand.Deactivate] - it is the marketplace asking whether the item sold - and
 * meaningless for the others, so it is omitted for them (the OLX client's `Json` sets
 * `explicitNulls = false`).
 */
@Serializable
internal data class AdvertCommandRequest(
    @SerialName("command") val command: String,
    @SerialName("is_success") val isSuccess: Boolean? = null,
)

/** The four lifecycle commands `POST adverts/{id}/commands` accepts. */
internal enum class AdvertCommand(val wireValue: String) {
    Activate("activate"),
    Deactivate("deactivate"),
    Finish("finish"),

    /** Rejected by OLX in some markets - see [com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry.supportsExtendCommand]. */
    Extend("extend"),
}
