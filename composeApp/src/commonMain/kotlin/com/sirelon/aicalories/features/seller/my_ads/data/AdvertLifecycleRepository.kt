package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.AdvertCommand
import com.sirelon.sellsnap.features.seller.auth.data.AdvertEditSnapshot
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiError
import com.sirelon.sellsnap.features.seller.auth.domain.OlxApiException
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertUpdateNestedKeys
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertUpdateOptionalKeys
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertUpdateRequiredKeys
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
     * OLX documents deactivate-then-delete as *the* way to remove an advert, with `DELETE` alone
     * accepted only while the advert is not `active`. [isActive] therefore decides whether the
     * deactivate half is needed up front, and [isSuccess] answers OLX's "did it sell?" for it.
     *
     * A listing that is not active can still be refused - a listing under moderation was, on a
     * real account, even though the docs name only `active` as non-deletable ("e.g. `active`" is
     * how they put it, so the list is open). OLX reports that refusal against `field: ad`, and the
     * answer to it is the documented removal path: take the listing down first, then delete. So a
     * refusal on `ad` retries as deactivate-then-delete instead of dead-ending, and any other
     * failure is passed straight through rather than being met with a command the seller did not
     * ask for.
     */
    suspend fun delete(localIndex: Int, advertId: Long, isActive: Boolean, isSuccess: Boolean?) {
        if (isActive) {
            deactivateThenDelete(localIndex, advertId, isSuccess ?: false)
            return
        }

        try {
            deleteAdvert(localIndex, advertId)
        } catch (error: Throwable) {
            if (!error.isAdvertStatusRefusal) throw error
            deactivateThenDelete(localIndex, advertId, isSuccess ?: false)
        }
    }

    private suspend fun deactivateThenDelete(localIndex: Int, advertId: Long, isSuccess: Boolean) {
        sendCommand(localIndex, advertId, AdvertCommand.Deactivate, isSuccess)

        try {
            deleteAdvert(localIndex, advertId)
        } catch (error: Throwable) {
            throw AdvertDeactivatedNotDeleted(error)
        }
    }

    private suspend fun deleteAdvert(localIndex: Int, advertId: Long) =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.deleteAdvert(accessToken, advertId)
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
 * Whether OLX refused a request because of the advert's status rather than anything about the
 * request itself. Both status refusals the docs list - "Ad has to be active" for a deactivate and
 * "Invalid status" for a delete - are reported against `field: ad`, and the field is the part to
 * match on: the titles alongside it come back in the market's own language.
 */
private val Throwable.isAdvertStatusRefusal: Boolean
    get() = ((this as? OlxApiException)?.error as? OlxApiError.ValidationError)?.field == "ad"

/**
 * Builds the `PUT adverts/{id}` body from what `GET adverts/{id}` returned, sending only the
 * fields and shapes the update endpoint's request schema defines.
 *
 * Echoing the response instead is what OLX refused with "compound forms expect an array or NULL on
 * submission" - its form layer's own words for a field receiving something it cannot read. The two
 * schemas are close, and differ in exactly two ways that matter:
 *
 * - The response carries keys the request does not define: `status`, `url`, `created_at`,
 *   `ad_delivery`, and whatever else a given market adds. Nothing outside
 *   [AdvertUpdateRequiredKeys] and [AdvertUpdateOptionalKeys] is an update field, and objects are
 *   narrowed to [AdvertUpdateNestedKeys] for the same reason.
 * - An attribute's value arrives under `value` when the attribute takes one and `values` when it
 *   takes several - two distinct keys, the unused one `null` - so the key it arrived under is the
 *   key that goes back. `value` is typed as a string while the response may answer with a number
 *   (`"value": 2015`).
 *
 * [priceValue] of null leaves the price untouched. A price on an advert that had none is built with
 * [fallbackCurrencyCode], since OLX requires a currency alongside a value.
 */
internal fun JsonObject.toUpdateBody(
    title: String,
    description: String,
    priceValue: Long?,
    fallbackCurrencyCode: String,
): JsonObject {
    val body = mutableMapOf<String, JsonElement>()

    body["title"] = JsonPrimitive(title)
    body["description"] = JsonPrimitive(description)
    this["category_id"]?.takeIf { it !is JsonNull }?.let { body["category_id"] = it }
    body["advertiser_type"] =
        this["advertiser_type"]?.takeIf { it !is JsonNull } ?: JsonPrimitive("private")

    (this["contact"] as? JsonObject)?.documentedProperties("contact")?.let { body["contact"] = it }
    advertLocation()?.documentedProperties("location")?.let { body["location"] = it }

    // Required, so an advert with no attributes still sends an empty array.
    body["attributes"] = JsonArray(
        (this["attributes"] as? JsonArray).orEmpty().mapNotNull { it.toSubmittableAttribute() },
    )

    if (priceValue != null || this["price"] is JsonObject) {
        val price = (this["price"] as? JsonObject)?.documentedProperties("price")
            ?.toMutableMap() ?: mutableMapOf()
        priceValue?.let { price["value"] = JsonPrimitive(it) }
        if (price["currency"] == null) price["currency"] = JsonPrimitive(fallbackCurrencyCode)
        body["price"] = JsonObject(price)
    }

    for (key in AdvertUpdateOptionalKeys) {
        if (key == "price") continue
        val value = this[key]?.takeIf { it !is JsonNull } ?: continue
        body[key] = when {
            key == "images" && value is JsonArray -> JsonArray(
                value.mapNotNull { (it as? JsonObject)?.documentedProperties("images") },
            )

            value is JsonObject && key in AdvertUpdateNestedKeys ->
                value.documentedProperties(key) ?: continue

            else -> value
        }
    }

    return JsonObject(body)
}

/**
 * [field] narrowed to the properties the update schema defines for it, or null when none of them
 * are present - an object of nothing but keys the endpoint does not model has nothing to send.
 */
private fun JsonObject.documentedProperties(field: String): JsonObject? {
    val documented = AdvertUpdateNestedKeys.getValue(field)
    val kept = filterKeys { it in documented }.filterValues { it !is JsonNull }
    return if (kept.isEmpty()) null else JsonObject(kept)
}

/**
 * `JsonNull` is itself a [JsonPrimitive], so reading `.content` off it yields the string "null" -
 * which is how an empty attribute once became `values: ["null"]`.
 */
private fun JsonElement?.contentOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/**
 * One attribute in the shape `PUT` accepts, keeping the key it arrived under: `values` for an
 * attribute that holds several, `value` for one that holds a single value. Numbers become strings
 * because that is how `value` is typed.
 *
 * Dropped when it carries no code or neither key - `code` is required, and an attribute with no
 * value at all says nothing.
 */
private fun JsonElement.toSubmittableAttribute(): JsonObject? {
    val attribute = this as? JsonObject ?: return null
    val code = attribute["code"].contentOrNull()?.takeIf { it.isNotBlank() } ?: return null

    val multiple = (attribute["values"] as? JsonArray)?.mapNotNull { it.contentOrNull() }
    if (!multiple.isNullOrEmpty()) {
        return JsonObject(
            mapOf(
                "code" to JsonPrimitive(code),
                "values" to JsonArray(multiple.map { JsonPrimitive(it) }),
            ),
        )
    }

    val single = attribute["value"].contentOrNull() ?: return null
    return JsonObject(mapOf("code" to JsonPrimitive(code), "value" to JsonPrimitive(single)))
}
