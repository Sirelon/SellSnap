package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.AdvertCommand
import com.sirelon.sellsnap.features.seller.auth.data.AdvertEditSnapshot
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertUpdateOptionalKeys
import com.sirelon.sellsnap.features.seller.auth.data.response.advertLocation
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatistics
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deleting a live listing takes two OLX calls - `deactivate`, then `DELETE` - because OLX only
 * accepts a delete while the advert is inactive. Thrown when the first half succeeded and the
 * second did not, so the seller can be told plainly that their listing is down but still there,
 * rather than being shown a generic failure for an action that half happened.
 */
class AdvertDeactivatedNotDeleted(cause: Throwable) :
    Exception("The listing was deactivated on OLX but could not be deleted.", cause)

/**
 * Lifecycle mutations on one account's adverts (SIR-98/101/103/104).
 *
 * Every OLX request is scoped to its own [withAccountToken] - never a whole multi-call sequence -
 * so a token refresh mid-sequence cannot replay a command that already landed. Sending
 * `deactivate` twice would fail with OLX's "Ad has to be active", which is exactly the kind of
 * phantom error the retry exists to avoid.
 *
 * Commands do not read the advert back. OLX answers 204 with no body and can take a moment to
 * settle a status, so a single-advert re-read straight afterwards was reporting the state the
 * listing had just left - the seller reopened the sheet and saw the same buttons. The caller
 * refetches the whole list instead, which is one call either way and is authoritative.
 */
internal class AdvertLifecycleRepository(
    private val accountRepository: SellerAccountRepository,
    private val unauthenticatedOlxApiClient: OlxApiClient,
    private val myAdvertsRepository: MyAdvertsRepository,
) {
    /**
     * `is_success` is required by OLX, not optional: the marketplace asks whether the item sold
     * and will not take the advert down without an answer.
     */
    suspend fun deactivate(localIndex: Int, advertId: Long, isSuccess: Boolean) =
        sendCommand(localIndex, advertId, AdvertCommand.Deactivate, isSuccess)

    suspend fun reactivate(localIndex: Int, advertId: Long) =
        sendCommand(localIndex, advertId, AdvertCommand.Activate, isSuccess = null)

    /** Not offered in the UI - see [AdvertAction]. Kept because SIR-98 covers all four commands. */
    suspend fun finish(localIndex: Int, advertId: Long) =
        sendCommand(localIndex, advertId, AdvertCommand.Finish, isSuccess = null)

    /** Rejected in Ukraine and Portugal - gate on `OlxCountry.supportsExtendCommand` before offering it. */
    suspend fun extend(localIndex: Int, advertId: Long) =
        sendCommand(localIndex, advertId, AdvertCommand.Extend, isSuccess = null)

    /**
     * [isActive] decides whether the deactivate half is needed at all, and [isSuccess] answers
     * OLX's "did it sell?" for that half. Deleting an already-inactive advert is a single call and
     * never asks.
     */
    suspend fun delete(localIndex: Int, advertId: Long, isActive: Boolean, isSuccess: Boolean?) {
        if (isActive) {
            sendCommand(localIndex, advertId, AdvertCommand.Deactivate, isSuccess ?: false)
        }

        try {
            accountRepository.withAccountToken(localIndex) { accessToken ->
                unauthenticatedOlxApiClient.deleteAdvert(accessToken, advertId)
            }
        } catch (error: Throwable) {
            if (isActive) throw AdvertDeactivatedNotDeleted(error)
            throw error
        }
    }

    suspend fun statistics(localIndex: Int, advertId: Long): OlxAdvertStatistics =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getAdvertStatistics(accessToken, advertId)
        }

    /** OLX's own explanation for an advert the app cannot act on, or null if it has none. */
    suspend fun moderationReason(localIndex: Int, advertId: Long): String? =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getAdvertModerationReason(accessToken, advertId)
        }

    suspend fun loadForEdit(localIndex: Int, advertId: Long): AdvertEditSnapshot =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getAdvert(accessToken, advertId)
        }

    /**
     * Sends an edit as the exact payload OLX just returned, with only the edited keys replaced
     * (SIR-104). [snapshot] must come from [loadForEdit] for the same advert: re-sending OLX's own
     * JSON is what guarantees that changing the price cannot drop an attribute, a delivery
     * setting, or any field this app does not model.
     *
     * [priceValue] of null leaves the price untouched. A price on an advert that had none is
     * built with [fallbackCurrencyCode], since OLX requires a currency alongside a value.
     */
    suspend fun applyEdit(
        localIndex: Int,
        snapshot: AdvertEditSnapshot,
        title: String,
        description: String,
        priceValue: Long?,
        fallbackCurrencyCode: String,
    ): MyAdvertItem? {
        val body = snapshot.updatePayload.toUpdateBody(
            title = title,
            description = description,
            priceValue = priceValue,
            fallbackCurrencyCode = fallbackCurrencyCode,
        )

        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.putAdvert(accessToken, snapshot.detail.id, body)
        }

        // Read the row back rather than trusting the PUT response: an edit can send an advert
        // back to moderation, and the seller must see the status it actually landed in.
        return refreshOrNull(localIndex, snapshot.detail.id)
    }

    private suspend fun sendCommand(
        localIndex: Int,
        advertId: Long,
        command: AdvertCommand,
        isSuccess: Boolean?,
    ) {
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.sendAdvertCommand(accessToken, advertId, command, isSuccess)
        }
    }

    /**
     * Only the edit path reads a single advert back, and only to decide whether to tell the
     * seller their change went to moderation. A failure to fetch it does not undo the edit and
     * must not be reported as one.
     */
    private suspend fun refreshOrNull(localIndex: Int, advertId: Long): MyAdvertItem? =
        runCatching { myAdvertsRepository.loadAdvert(localIndex, advertId) }.getOrNull()
}

/**
 * Builds the `PUT adverts/{id}` body from what `GET adverts/{id}` returned, following the update
 * endpoint's documented request schema rather than echoing the response.
 *
 * Echoing was wrong because the two schemas are **not** the same shape, and the OLX docs' own
 * response sample is the only place that says so:
 *
 * - `location` comes back nested inside `contact`, but `PUT` takes it as a **required top-level
 *   field**. An echo therefore omitted something required and included it where the form does not
 *   model it. This is what OLX was rejecting with "compound forms expect an array or NULL on
 *   submission".
 * - `delivery_change_allowed` is a top-level sibling of `ad_delivery` in the response and is not
 *   in the request at all.
 * - An attribute comes back as a scalar `value` with `values: null`, sometimes numeric
 *   (`"value": 2015`), while the request wants `values` as an array of strings.
 *
 * So only the documented fields are sent: the seven [AdvertUpdateRequiredKeys], plus each of
 * [AdvertUpdateOptionalKeys] the advert actually has, so an edit does not cost the seller a
 * setting they never touched. `auto_extend_enabled` is deliberately never sent - it is the one
 * field the spec documents as unchanged when omitted.
 */
private fun JsonObject.toUpdateBody(
    title: String,
    description: String,
    priceValue: Long?,
    fallbackCurrencyCode: String,
): JsonObject {
    val body = mutableMapOf<String, JsonElement>()

    body["title"] = JsonPrimitive(title)
    body["description"] = JsonPrimitive(description)
    this["category_id"]?.takeIf { it !is JsonNull }?.let { body["category_id"] = it }
    body["advertiser_type"] = this["advertiser_type"]?.takeIf { it !is JsonNull } ?: JsonPrimitive("private")

    // Only the two fields the contact form models; the response also nests `location` in here.
    (this["contact"] as? JsonObject)?.let { contact ->
        body["contact"] = JsonObject(contact.filterKeys { it == "name" || it == "phone" })
    }

    advertLocation()?.let { location ->
        body["location"] = JsonObject(
            location.filterKeys { it in setOf("city_id", "district_id", "latitude", "longitude") }
                .filterValues { it !is JsonNull },
        )
    }

    // Required, so an advert with no attributes still sends an empty array.
    body["attributes"] = JsonArray(
        (this["attributes"] as? JsonArray).orEmpty().mapNotNull { it.toSubmittableAttribute() },
    )

    if (priceValue != null || this["price"] is JsonObject) {
        val price = (this["price"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        priceValue?.let { price["value"] = JsonPrimitive(it) }
        if (price["currency"].let { it == null || it is JsonNull }) {
            price["currency"] = JsonPrimitive(fallbackCurrencyCode)
        }
        body["price"] = JsonObject(price.filterValues { it !is JsonNull })
    }

    // Forwarded only when the advert has them: the response returns `null` for the ones that do
    // not apply, and sending a null where the form expects a structure is how this broke before.
    for (key in AdvertUpdateOptionalKeys) {
        if (key == "price") continue
        val value = this[key]?.takeIf { it !is JsonNull } ?: continue
        body[key] = if (key == "ad_delivery" && value is JsonObject) {
            // OLX reports `delivery_change_allowed` - top-level in the docs' sample, nested in
            // practice - and accepts it in neither request schema.
            JsonObject(value.filterKeys { it != "delivery_change_allowed" })
        } else {
            value
        }
    }

    return JsonObject(body)
}

/**
 * One attribute in the shape `PUT` accepts: `code` plus `values` as an array of strings.
 *
 * The response gives a scalar `value` with `values: null`, and the scalar is not always a string -
 * the docs' own sample has `"value": 2015`. Dropped entirely if it carries no code or no value,
 * since an attribute with neither says nothing and `code` is required.
 */
/** `JsonNull` is itself a [JsonPrimitive], so reading `.content` off it yields the string
 * "null" - which is how an empty attribute became `values: ["null"]`. */
private fun JsonElement?.contentOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonElement.toSubmittableAttribute(): JsonObject? {
    val attribute = this as? JsonObject ?: return null
    val code = (attribute["code"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return null

    val values = (attribute["values"] as? JsonArray)
        ?.mapNotNull { it.contentOrNull() }
        ?: attribute["value"].contentOrNull()?.let { listOf(it) }
        ?: return null

    if (values.isEmpty()) return null
    return JsonObject(
        mapOf(
            "code" to JsonPrimitive(code),
            "values" to JsonArray(values.map { JsonPrimitive(it) }),
        ),
    )
}
