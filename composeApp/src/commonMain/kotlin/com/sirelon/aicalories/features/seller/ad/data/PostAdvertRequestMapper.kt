package com.sirelon.sellsnap.features.seller.ad.data

import com.sirelon.sellsnap.features.seller.ad.preview_ad.OlxAttributeState
import com.sirelon.sellsnap.features.seller.auth.data.AdvertAttributeRequest
import com.sirelon.sellsnap.features.seller.auth.data.AdvertContactRequest
import com.sirelon.sellsnap.features.seller.auth.data.AdvertImageRequest
import com.sirelon.sellsnap.features.seller.auth.data.AdvertLocationRequest
import com.sirelon.sellsnap.features.seller.auth.data.AdvertPriceRequest
import com.sirelon.sellsnap.features.seller.auth.data.PostAdvertRequest
import com.sirelon.sellsnap.features.seller.categories.domain.AttributeInputType
import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import kotlin.math.roundToInt

internal object PostAdvertRequestMapper {

    fun map(
        title: String,
        description: String,
        category: OlxCategory,
        location: OlxLocation,
        images: List<String>,
        price: Float,
        contactName: String,
        attributeItems: List<OlxAttributeState> = emptyList(),
    ): PostAdvertRequest = PostAdvertRequest(
        title = title,
        description = description,
        categoryId = category.id,
        advertiserType = "private",
        contact = AdvertContactRequest(name = contactName, phone = null),
        location = AdvertLocationRequest(cityId = location.cityId, districtId = location.districtId),
        images = images.map { AdvertImageRequest(url = it) },
        price = AdvertPriceRequest(value = price.roundToInt(), currency = "UAH", negotiable = false),
        attributes = attributeItems
            .filter { it.selectedValues.isNotEmpty() }
            .map { item ->
                val values = when (item.attribute.inputType) {
                    AttributeInputType.SingleSelect, AttributeInputType.MultiSelect ->
                        item.selectedValues.map { it.code }
                    AttributeInputType.NumericInput, AttributeInputType.TextInput ->
                        item.selectedValues.map { it.label }
                }
                AdvertAttributeRequest(code = item.attribute.code, values = values)
            },
    )
}
