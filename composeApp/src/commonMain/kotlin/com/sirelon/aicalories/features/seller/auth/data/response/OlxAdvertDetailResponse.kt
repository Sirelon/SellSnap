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
 * typed view: `PUT adverts/{id}` takes the whole advert with no patch semantics, so an edit has to
 * send back every field it is not changing, including ones this app does not model. The raw object
 * is the source those are read from; `toUpdateBody` reshapes it into the update schema, which is
 * not the same shape this response arrives in - see `SPIKE-SIR-99-advert-edit-round-trip.md`.
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
 * The keys `PUT adverts/{id}` requires, per the spec's `required:` list. An update that omits one
 * of these is rejected.
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
 * The rest of the keys `PUT adverts/{id}` defines. Each is forwarded when the advert has it, so an
 * edit does not cost the seller a setting the app never asked them about.
 *
 * This is the endpoint's whole vocabulary together with [AdvertUpdateRequiredKeys] - a response
 * key absent from both is not an update field and does not go back. `auto_extend_enabled` is the
 * one exception: it is defined, but the spec documents omitting it as leaving auto-renew
 * unchanged, which is exactly what an edit should do, so it is never sent.
 */
internal val AdvertUpdateOptionalKeys = setOf(
    "external_url",
    "external_id",
    "images",
    "price",
    "salary",
    "courier",
    "product_safety_regulation",
)

/**
 * The properties `PUT adverts/{id}` defines inside each of its object fields, and for `images` the
 * properties of one array item.
 *
 * `GET adverts/{id}` answers with more than these - the app has seen `delivery_change_allowed`
 * arrive alongside a delivery block - so a field is narrowed to this list before it is sent.
 *
 * `product_safety_regulation` is absent on purpose: request and response both describe it with the
 * same `$ref`, so what comes back is already the shape that goes out.
 */
internal val AdvertUpdateNestedKeys: Map<String, Set<String>> = mapOf(
    "contact" to setOf("name", "phone"),
    "location" to setOf("city_id", "district_id", "latitude", "longitude"),
    "price" to setOf("value", "currency", "negotiable", "trade", "budget"),
    "salary" to setOf("value_from", "value_to", "currency", "negotiable", "type"),
    "images" to setOf("url"),
)

/**
 * The advert's location. The spec places it at the top level of the advert, which is where this
 * looks first; the fallback covers a response that nests it under `contact` instead, since `PUT`
 * requires the field and an edit that omits it is refused outright.
 */
internal fun JsonObject.advertLocation(): JsonObject? =
    this["location"] as? JsonObject
        ?: (this["contact"] as? JsonObject)?.get("location") as? JsonObject
