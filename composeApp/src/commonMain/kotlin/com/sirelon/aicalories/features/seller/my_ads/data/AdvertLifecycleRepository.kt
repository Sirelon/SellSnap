package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.AdvertCommand
import com.sirelon.sellsnap.features.seller.auth.data.AdvertEditSnapshot
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.my_ads.domain.AdvertAction
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertDeliveryResponseOnlyKeys
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
        val body = snapshot.updatePayload.withEdits(
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
 * Attributes are the one place the response and the request genuinely differ in shape, so echoing
 * them back verbatim is not enough.
 *
 * OLX returns a single-valued attribute as a scalar `value`, but its submission validator answers
 * "compound forms expect an array or NULL on submission" for the same attribute - a real edit
 * failed on exactly this. `values` as an array is the shape known to be accepted, because that is
 * what the publish path has always sent for every attribute type
 * ([com.sirelon.sellsnap.features.seller.ad.data.PostAdvertRequestMapper] wraps even a
 * single-select code in a one-element list).
 *
 * So a scalar becomes a one-element array, an existing array is left alone, and an attribute with
 * neither is submitted as NULL, which the same message says is acceptable. This closes residual
 * risk (b) in `SPIKE-SIR-99-advert-edit-round-trip.md`.
 */
private fun JsonElement.asSubmittableAttribute(): JsonElement {
    val attribute = this as? JsonObject ?: return this
    val submitted = attribute.toMutableMap()

    val values = attribute["values"] as? JsonArray
        ?: (attribute["value"]?.takeIf { it !is JsonNull })?.let { JsonArray(listOf(it)) }
    submitted["values"] = values ?: JsonNull
    submitted.remove("value")

    return JsonObject(submitted)
}

private fun JsonObject.withEdits(
    title: String,
    description: String,
    priceValue: Long?,
    fallbackCurrencyCode: String,
): JsonObject {
    val edited = toMutableMap()
    edited["title"] = JsonPrimitive(title)
    edited["description"] = JsonPrimitive(description)

    if (priceValue != null) {
        val price = (this["price"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        price["value"] = JsonPrimitive(priceValue)
        // An explicit `null` currency counts as absent: OLX will not take a value without one,
        // and `getOrPut` alone would leave the null in place because the key exists.
        if (price["currency"].let { it == null || it is JsonNull }) {
            price["currency"] = JsonPrimitive(fallbackCurrencyCode)
        }
        edited["price"] = JsonObject(price)
    }

    // `ad_delivery.delivery_change_allowed` is OLX telling us whether delivery is editable, and
    // is absent from the request schema. It is nested, so the top-level
    // [AdvertResponseOnlyKeys] filter in `OlxApiClient.getAdvert` cannot reach it.
    (edited["ad_delivery"] as? JsonObject)?.let { delivery ->
        edited["ad_delivery"] = JsonObject(delivery.filterKeys { it !in AdvertDeliveryResponseOnlyKeys })
    }

    (edited["attributes"] as? JsonArray)?.let { attributes ->
        edited["attributes"] = JsonArray(attributes.map { it.asSubmittableAttribute() })
    }

    return JsonObject(edited)
}
