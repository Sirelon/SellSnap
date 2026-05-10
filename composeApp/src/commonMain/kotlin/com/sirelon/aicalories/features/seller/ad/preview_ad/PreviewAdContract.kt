package com.sirelon.sellsnap.features.seller.ad.preview_ad

import com.sirelon.sellsnap.features.seller.ad.publish_success.PublishSuccessData
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttribute
import com.sirelon.sellsnap.features.seller.categories.domain.OlxAttributeValue
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.categories.domain.ValidationError
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import kotlin.jvm.JvmInline

data class OlxAttributeState(
    val attribute: OlxAttribute,
    val selectedValues: List<OlxAttributeValue> = emptyList(),
    val error: ValidationError? = null,
)

interface PreviewAdContract {

    data class PreviewAdState(
        val categoryLabel: String,
        val selectedCategory: OlxCategory? = null,
        val isPublishing: Boolean = false,
        val generationElapsedMs: Long = 0L,
        val isSessionResolved: Boolean = false,
        val price: Float,
        val minPrice: Float,
        val maxPrice: Float,
        val images: List<String>,
        val attributes: List<OlxAttribute> = emptyList(),
        val location: OlxLocation? = null,
        val locationLoading: Boolean = false,
        val attributeItems: List<OlxAttributeState> = emptyList(),
        val isGuest: Boolean = false,
    )

    sealed interface PreviewAdEvent {
        data class CategorySelected(val category: OlxCategory) : PreviewAdEvent

        data object OnChangeCategoryClick : PreviewAdEvent
        data object Publish : PreviewAdEvent

        @JvmInline
        value class OnPriceChanged(val price: Float) : PreviewAdEvent

        data object FetchLocation : PreviewAdEvent
        data object RefreshLocationClicked : PreviewAdEvent

        data class AttributeValueChanged(
            val attributeCode: String,
            val values: List<OlxAttributeValue>,
        ) : PreviewAdEvent
    }

    sealed interface PreviewAdEffect {
        data class ShowMessage(val message: String) : PreviewAdEffect
        data object GoToGategoryPicker : PreviewAdEffect
        data object NavigateToPublishing : PreviewAdEffect
        data class PublishSuccess(val data: PublishSuccessData) : PreviewAdEffect
        data class PublishFailure(val message: String) : PreviewAdEffect
        data class NavigateToProfile(val reason: String) : PreviewAdEffect
    }
}
