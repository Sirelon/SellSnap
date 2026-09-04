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
 * The top-level keys `PUT adverts/{id}` documents in its request body, per the OpenAPI spec. An
 * edit sends these and nothing else.
 *
 * An allowlist rather than a list of keys to strip. Blind echoing of the whole `GET` response was
 * refused by OLX with "compound forms expect an array or NULL on submission" - a Symfony Forms
 * error meaning some nested field received the wrong kind of value. Rather than hunt the one
 * offending key, only documented keys are sent, which removes the whole class: a field OLX returns
 * but its update form does not model can no longer reach the request.
 *
 * Every documented key is still forwarded when present, so the reason for echoing in the first
 * place holds - `PUT` resets what it is not sent, and a seller changing their price must not lose
 * their delivery settings or attributes.
 *
 * Keys deliberately absent because the response carries them and the request does not: `id`,
 * `status`, `url`, `created_at`, `activated_at`, `valid_to`.
 */
internal val AdvertUpdateAllowedKeys = setOf(
    "title",
    "description",
    "category_id",
    "advertiser_type",
    "external_url",
    "external_id",
    "contact",
    "location",
    "images",
    "price",
    "salary",
    "attributes",
    "courier",
    "ad_delivery",
    "auto_extend_enabled",
    "product_safety_regulation",
)

/**
 * The same, nested inside `ad_delivery`. `delivery_change_allowed` is OLX reporting whether
 * delivery settings may currently be edited; the request schema accepts only
 * `delivery_package_ids`. Kept separate because it needs stripping one level down - see
 * `SPIKE-SIR-99-advert-edit-round-trip.md`.
 */
internal val AdvertDeliveryResponseOnlyKeys = setOf("delivery_change_allowed")
