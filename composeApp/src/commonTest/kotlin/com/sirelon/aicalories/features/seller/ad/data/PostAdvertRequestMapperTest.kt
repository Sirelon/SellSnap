package com.sirelon.sellsnap.features.seller.ad.data

import com.sirelon.sellsnap.features.seller.categories.domain.OlxCategory
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.location.OlxLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class PostAdvertRequestMapperTest {

    @Test
    fun `map uses loaded currency code for advert price payload`() {
        val request = PostAdvertRequestMapper.map(
            title = "Clean city bike",
            description = "A clean city bike with working brakes and fresh tires.",
            category = OlxCategory(
                id = 123,
                label = "Bikes",
                parentId = null,
                isLeaf = true,
            ),
            location = OlxLocation(
                cityId = 1,
                cityName = "Kyiv",
                districtId = null,
                districtName = null,
            ),
            images = listOf("https://example.com/bike.jpg"),
            price = 1500f,
            currency = OlxCurrency(
                code = "PLN",
                label = "zł",
                isDefault = true,
            ),
            contactName = "Seller",
        )

        assertEquals("PLN", request.price?.currency)
        assertEquals(1500, request.price?.value)
    }
}
