package com.sirelon.sellsnap.features.seller.auth.data.response

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertDetail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `GET adverts/{id}` - one advert, richer than the list item [OlxAdvertResponse] models.
 *
 * [OlxAdvertDetailRootResponse.data] is deliberately kept as a raw [JsonObject] alongside the
 * typed view: `PUT adverts/{id}` takes the full create payload with no patch semantics, so an
 * edit has to send every field back. Echoing the untouched fields as the exact JSON OLX just
 * returned is the only way to guarantee that a seller who changes the price cannot lose their
 * attributes, delivery settings, or a field this app does not model at all - see
 * `SPIKE-SIR-99-advert-edit-round-trip.md`.
 */
@Serializable
internal class OlxAdvertDetailRootResponse(
    @SerialName("data")
    val data: JsonObject?,
)

@Serializable
internal class OlxAdvertDetailResponse(
    @SerialName("id")
    val id: Long?,

    @SerialName("status")
    val status: String?,

    @SerialName("url")
    val url: String?,

    @SerialName("title")
    val title: String?,

    @SerialName("description")
    val description: String?,

    @SerialName("category_id")
    val categoryId: Int?,

    @SerialName("created_at")
    val createdAt: String?,

    @SerialName("valid_to")
    val validTo: String?,

    @SerialName("images")
    val images: List<OlxAdvertImageResponse>?,

    @SerialName("price")
    val price: OlxAdvertPriceResponse?,

    @SerialName("auto_extend_enabled")
    val autoExtendEnabled: Boolean?,
) {
    fun toDomain(): OlxAdvertDetail? {
        val advertId = id ?: return null
        return OlxAdvertDetail(
            id = advertId,
            title = title.orEmpty(),
            description = description.orEmpty(),
            status = AdvertStatus.from(status.orEmpty()),
            url = url.orEmpty(),
            categoryId = categoryId,
            price = price?.toDomain(),
            imageUrls = images.orEmpty().mapNotNull { it.url?.takeIf(String::isNotBlank) },
            createdAt = createdAt.orEmpty(),
            validTo = validTo.orEmpty(),
            autoExtendEnabled = autoExtendEnabled ?: false,
        )
    }
}

/**
 * Top-level keys `GET adverts/{id}` returns that `PUT adverts/{id}` does not accept, per the
 * OpenAPI spec's `Advert` schema vs the update request body. Stripped before an edit is sent back.
 */
internal val AdvertResponseOnlyKeys = setOf("id", "status", "url", "created_at", "activated_at", "valid_to")

/**
 * The same, nested inside `ad_delivery`. `delivery_change_allowed` is OLX reporting whether
 * delivery settings may currently be edited; the request schema accepts only
 * `delivery_package_ids`. Kept separate because it needs stripping one level down - see
 * `SPIKE-SIR-99-advert-edit-round-trip.md`.
 */
internal val AdvertDeliveryResponseOnlyKeys = setOf("delivery_change_allowed")
