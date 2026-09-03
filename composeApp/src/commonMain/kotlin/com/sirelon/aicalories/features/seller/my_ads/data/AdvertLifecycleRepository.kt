package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.AdvertCommand
import com.sirelon.sellsnap.features.seller.auth.data.AdvertEditSnapshot
import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertResponseOnlyKeys
import com.sirelon.sellsnap.features.seller.auth.data.response.AdvertDeliveryResponseOnlyKeys
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatistics
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deleting a live listing takes two OLX calls - `deactivate`, then `DELETE` - because OLX only
 * accepts a delete while the advert is inactive. Thrown when the first half succeeded and the
 * second did not, so the seller can be told plainly that their listing is down but still there,
 * rather than being shown a generic failure for an action that half happened.
 */
class AdvertDeactivatedNotDeleted(val advert: MyAdvertItem?, cause: Throwable) :
    Exception("The listing was deactivated on OLX but could not be deleted.", cause)

/**
 * Lifecycle mutations on one account's adverts (SIR-98/101/103/104).
 *
 * Every OLX request is scoped to its own [withAccountToken] - never a whole multi-call sequence -
 * so a token refresh mid-sequence cannot replay a command that already landed. Sending
 * `deactivate` twice would fail with OLX's "Ad has to be active", which is exactly the kind of
 * phantom error the retry exists to avoid.
 *
 * Reading a row back after a mutation is deliberately best-effort. A command answers 204 and is
 * irreversible the moment it lands; if the follow-up `GET adverts/{id}` then fails on a network
 * blip, the action still succeeded, and reporting it as a failure would send the seller round to
 * try again against a status the advert has already left.
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
    suspend fun deactivate(localIndex: Int, advertId: Long, isSuccess: Boolean): MyAdvertItem? =
        commandThenRefresh(localIndex, advertId, AdvertCommand.Deactivate, isSuccess)

    suspend fun reactivate(localIndex: Int, advertId: Long): MyAdvertItem? =
        commandThenRefresh(localIndex, advertId, AdvertCommand.Activate)

    suspend fun finish(localIndex: Int, advertId: Long): MyAdvertItem? =
        commandThenRefresh(localIndex, advertId, AdvertCommand.Finish)

    /** Rejected in Ukraine and Portugal - gate on `OlxCountry.supportsExtendCommand` before offering it. */
    suspend fun extend(localIndex: Int, advertId: Long): MyAdvertItem? =
        commandThenRefresh(localIndex, advertId, AdvertCommand.Extend)

    /**
     * [isActive] decides whether the deactivate half is needed at all, and [isSuccess] answers
     * OLX's "did it sell?" for that half. Deleting an already-inactive advert is a single call and
     * never asks.
     */
    suspend fun delete(localIndex: Int, advertId: Long, isActive: Boolean, isSuccess: Boolean?) {
        // Only the command, with no row refresh in between: the refresh is worthless here (the
        // advert is about to be deleted) and a blip on it must not abort a delete whose first
        // half has already landed.
        if (isActive) {
            sendCommand(localIndex, advertId, AdvertCommand.Deactivate, isSuccess ?: false)
        }

        try {
            accountRepository.withAccountToken(localIndex) { accessToken ->
                unauthenticatedOlxApiClient.deleteAdvert(accessToken, advertId)
            }
        } catch (error: Throwable) {
            if (isActive) throw AdvertDeactivatedNotDeleted(refreshOrNull(localIndex, advertId), error)
            throw error
        }
    }

    suspend fun statistics(localIndex: Int, advertId: Long): OlxAdvertStatistics =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getAdvertStatistics(accessToken, advertId)
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

    private suspend fun commandThenRefresh(
        localIndex: Int,
        advertId: Long,
        command: AdvertCommand,
        isSuccess: Boolean? = null,
    ): MyAdvertItem? {
        sendCommand(localIndex, advertId, command, isSuccess)
        return refreshOrNull(localIndex, advertId)
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
     * The new state of the row, or null if it could not be read. Commands answer 204 with no
     * body and OLX may resolve a status differently from what was requested, so the row has to
     * come from the server - but a failure to fetch it does not undo the command, and must not be
     * reported as one.
     */
    private suspend fun refreshOrNull(localIndex: Int, advertId: Long): MyAdvertItem? =
        runCatching { myAdvertsRepository.loadAdvert(localIndex, advertId) }.getOrNull()
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

    return JsonObject(edited)
}
