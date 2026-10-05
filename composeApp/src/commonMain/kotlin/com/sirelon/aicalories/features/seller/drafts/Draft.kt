package com.sirelon.sellsnap.features.seller.drafts

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.ad.AdvertisementWithAttributes

/**
 * A generated listing the seller has not published yet.
 *
 * [id] is the listing's `generationSessionId`, so the preview can find and update its own draft
 * without carrying a second identifier. [listing] starts as the model produced it and follows the
 * seller's edits in the preview (title, description, price, photos). A draft is deleted the moment
 * its listing is published on OLX.
 */
@Immutable
data class Draft(
    val id: String,
    val countryCode: String,
    val createdAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val listing: AdvertisementWithAttributes,
)
