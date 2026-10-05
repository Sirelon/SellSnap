package com.sirelon.sellsnap.features.seller.ad

import androidx.compose.runtime.Immutable
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class AdvertisementWithAttributes(
    val advertisement: Advertisement,
    val filledAttributes: Map<String, List<OlxAttributeValue>>,
    val sellerPrompt: String = "",
    val generationSessionId: String? = null,
    val lastAttemptId: String? = null,
    /**
     * The price the seller set in the preview. Null until they have: [Advertisement.suggestedPrice]
     * stays the model's number, which `AdvertOutcomeStore.recordPublished` compares against the
     * published price. Defaulted so stored drafts and saved navigation keys without it still decode.
     */
    val sellerPrice: Float? = null,
    /**
     * The OLX category the seller has in the preview, once one is selected there. Null means the
     * preview suggests one from the title on open, as it does for a fresh generation.
     */
    val selectedCategoryId: Int? = null,
    /**
     * The `external_id` a publish attempt for this listing already sent to OLX, if one left the
     * device. A reopened draft carries it so the next Publish asks OLX before posting again - see
     * `PreviewAdViewModel.postAdvertIdempotently`.
     */
    val publishExternalId: String? = null,
)