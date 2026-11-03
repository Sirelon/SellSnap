package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.data.OlxApiClient
import com.sirelon.sellsnap.features.seller.my_ads.model.AdvertListingSnapshot
import com.sirelon.sellsnap.features.seller.my_ads.model.ListingAttribute
import com.sirelon.sellsnap.features.seller.my_ads.model.MyAdvertItem
import com.sirelon.sellsnap.features.seller.profile.data.SellerAccountRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class MyAdvertsRepository(
    private val accountRepository: SellerAccountRepository,
    private val unauthenticatedOlxApiClient: OlxApiClient,
) {
    /** One page of one account's adverts. See [withAccountToken] for why the token is explicit. */
    suspend fun loadAdverts(localIndex: Int, offset: Int, limit: Int): List<MyAdvertItem> =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient
                .getCurrentUserAdverts(accessToken, offset = offset, limit = limit)
                .map(MyAdvertItemMapper::map)
        }

    /**
     * Re-reads one advert after a lifecycle command (SIR-101). OLX may resolve a status
     * differently from what was requested - a `finish` can land somewhere other than
     * `RemovedByUser`, and an edit can send an advert back to moderation - so the row is refreshed
     * from the server rather than being patched from the action that was attempted.
     */
    suspend fun loadAdvert(localIndex: Int, advertId: Long): MyAdvertItem =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            MyAdvertItemMapper.map(unauthenticatedOlxApiClient.getAdvert(accessToken, advertId).detail)
        }

    /** [loadAdvert], with `null` when OLX answers 404: the advert no longer exists. */
    suspend fun loadAdvertOrNull(localIndex: Int, advertId: Long): MyAdvertItem? =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getAdvertOrNull(accessToken, advertId)
                ?.let { MyAdvertItemMapper.map(it.detail) }
        }

    /**
     * [loadAdvertOrNull], plus the listing's description and attributes from the same request:
     * the conversation's one advert read also feeds the reply suggestions (SIR-96).
     */
    suspend fun loadAdvertSnapshotOrNull(localIndex: Int, advertId: Long): AdvertListingSnapshot? =
        accountRepository.withAccountToken(localIndex) { accessToken ->
            unauthenticatedOlxApiClient.getAdvertOrNull(accessToken, advertId)?.let { snapshot ->
                AdvertListingSnapshot(
                    item = MyAdvertItemMapper.map(snapshot.detail),
                    description = snapshot.detail.description,
                    attributes = listingAttributes(snapshot.updatePayload),
                )
            }
        }
}

/**
 * The `attributes` of a `GET adverts/{id}` `data` object. An attribute holds `values` (several) or
 * `value` (one, which may arrive as a number); either may be null. One without a code or any
 * value is dropped.
 */
internal fun listingAttributes(advert: JsonObject): List<ListingAttribute> =
    (advert["attributes"] as? JsonArray).orEmpty().mapNotNull { element ->
        val attribute = element as? JsonObject ?: return@mapNotNull null
        val code = attribute["code"].contentOrNullSafe()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val values = buildList {
            (attribute["values"] as? JsonArray)?.forEach { value -> value.contentOrNullSafe()?.let(::add) }
            attribute["value"].contentOrNullSafe()?.let(::add)
        }.filter { it.isNotBlank() }
        if (values.isEmpty()) null else ListingAttribute(code = code, values = values)
    }

private fun JsonElement?.contentOrNullSafe(): String? = (this as? JsonPrimitive)?.contentOrNull
