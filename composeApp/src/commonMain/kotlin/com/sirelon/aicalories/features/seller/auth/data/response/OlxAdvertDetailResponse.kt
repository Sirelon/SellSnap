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
 * The keys `PUT adverts/{id}` requires, per the spec's `required:` list: everything else in its
 * request body is optional. An update that omits one of these is rejected.
 */
internal val AdvertUpdateRequiredKeys = setOf(
    "title",
    "description",
    "category_id",
    "advertiser_type",
    "contact",
    "location",
    "attributes",
)

/**
 * Optional keys `PUT adverts/{id}` accepts and this app forwards when the advert has them, so an
 * edit does not cost the seller a setting it never asked about. Only `auto_extend_enabled` is
 * documented as unchanged when omitted, so it is deliberately never sent.
 */
internal val AdvertUpdateOptionalKeys = setOf(
    "external_url",
    "external_id",
    "images",
    "price",
    "salary",
    "courier",
    "ad_delivery",
    "product_safety_regulation",
)

/**
 * `GET adverts/{id}` nests the advert's location **inside `contact`**, while `PUT` takes
 * `location` as a required top-level field. Reading the response's own sample in the OLX docs is
 * the only way to know this, and getting it wrong means every edit is rejected: the location is
 * missing where it is required and present where the form does not model it.
 *
 * Read from the top level first anyway, in case a market or a future version puts it there.
 */
internal fun JsonObject.advertLocation(): JsonObject? =
    this["location"] as? JsonObject
        ?: (this["contact"] as? JsonObject)?.get("location") as? JsonObject
