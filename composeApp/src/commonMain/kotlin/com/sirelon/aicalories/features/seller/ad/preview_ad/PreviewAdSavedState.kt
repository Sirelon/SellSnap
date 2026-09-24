package com.sirelon.sellsnap.features.seller.ad.preview_ad

import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import kotlinx.serialization.Serializable

@Serializable
data class PreviewAdSavedState(
    val title: String? = null,
    val description: String? = null,
    val price: Float? = null,
    val selectedCategoryId: Int? = null,
    val attributeValues: Map<String, List<OlxAttributeValue>> = emptyMap(),
    val location: OlxLocation? = null,
    val publishSuccessData: PublishSuccessData? = null,
    /**
     * The `external_id` sent with this listing's `POST adverts`, minted just before the first
     * attempt and kept for the life of the draft.
     *
     * Its presence is the record that a POST for this listing already left the device, which is
     * the one thing a restored screen otherwise has no way of knowing: [publishSuccessData] is
     * only written once a response comes back, so a process death mid-publish restores an
     * ordinary-looking draft sitting on top of an advert that may well be live. Non-null here
     * means the next Publish tap asks OLX before it posts.
     */
    val publishExternalId: String? = null,
)
