package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvert
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertPrice
import kotlin.test.Test
import kotlin.test.assertEquals

class MyAdvertItemMapperTest {

    @Test
    fun `map formats uah price and keeps status`() {
        val item = MyAdvertItemMapper.map(
            OlxAdvert(
                id = 42,
                title = "Sneakers",
                status = AdvertStatus.Active,
                url = "https://www.olx.ua/item",
                primaryImageUrl = "https://example.com/image.jpg",
                price = OlxAdvertPrice(value = 12500L, currency = "UAH", negotiable = false),
                createdAt = "2026-05-01T10:00:00+03:00",
                validTo = "2026-06-01T10:00:00+03:00",
            ),
        )

        assertEquals(42L, item.id)
        assertEquals("Sneakers", item.title)
        assertEquals(AdvertStatus.Active, item.status)
        assertEquals("₴ 12 500", item.priceFormatted)
        assertEquals("https://example.com/image.jpg", item.primaryImageUrl)
        assertEquals("2026-05-01T10:00:00+03:00", item.createdAt)
        assertEquals("2026-06-01T10:00:00+03:00", item.validTo)
        // The raw figure is carried alongside the formatted one because the sold-price prompt and
        // the price edit both pre-fill from it - reparsing "₴ 12 500" back to a number would be
        // locale-dependent and lossy.
        assertEquals(12500L, item.priceValue)
        assertEquals("UAH", item.currencyCode)
    }

    @Test
    fun `map leaves price blank when olx price is missing`() {
        val item = MyAdvertItemMapper.map(
            OlxAdvert(
                id = 43,
                title = "Phone",
                status = AdvertStatus.Limited,
                url = "",
                primaryImageUrl = null,
                price = null,
                createdAt = "",
                validTo = "",
            ),
        )

        assertEquals("", item.priceFormatted)
        assertEquals("", item.url)
        // No price to pre-fill with, rather than a misleading zero.
        assertEquals(null, item.priceValue)
        assertEquals("", item.currencyCode)
    }

    @Test
    fun `status mapping supports known olx lifecycle values`() {
        assertEquals(AdvertStatus.New, AdvertStatus.from("new"))
        assertEquals(AdvertStatus.Active, AdvertStatus.from("active"))
        assertEquals(AdvertStatus.Limited, AdvertStatus.from("limited"))
        assertEquals(AdvertStatus.RemovedByUser, AdvertStatus.from("removed_by_user"))
        assertEquals(AdvertStatus.Outdated, AdvertStatus.from("outdated"))
        assertEquals(AdvertStatus.Unconfirmed, AdvertStatus.from("unconfirmed"))
        assertEquals(AdvertStatus.Unpaid, AdvertStatus.from("unpaid"))
        assertEquals(AdvertStatus.Moderated, AdvertStatus.from("moderated"))
        assertEquals(AdvertStatus.Blocked, AdvertStatus.from("blocked"))
        assertEquals(AdvertStatus.Disabled, AdvertStatus.from("disabled"))
        assertEquals(AdvertStatus.RemovedByModerator, AdvertStatus.from("removed_by_moderator"))
        assertEquals(AdvertStatus.Unknown, AdvertStatus.from("unexpected"))
    }
}
