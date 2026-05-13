package com.sirelon.sellsnap.features.seller.my_ads.data

import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvert
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertPrice
import com.sirelon.sellsnap.features.seller.auth.domain.OlxAdvertStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MyAdvertItemMapperTest {

    @Test
    fun `map formats uah price and keeps status`() {
        val item = MyAdvertItemMapper.map(
            OlxAdvert(
                id = 42,
                title = "Sneakers",
                status = OlxAdvertStatus.Active,
                url = "https://www.olx.ua/item",
                primaryImageUrl = "https://example.com/image.jpg",
                price = OlxAdvertPrice(value = 12500f, currency = "UAH", negotiable = false),
                createdAt = "2026-05-01T10:00:00+03:00",
                validTo = "2026-06-01T10:00:00+03:00",
            ),
        )

        assertEquals(42L, item.id)
        assertEquals("Sneakers", item.title)
        assertEquals(OlxAdvertStatus.Active, item.status)
        assertEquals("₴ 12 500", item.priceFormatted)
        assertEquals("https://example.com/image.jpg", item.primaryImageUrl)
        assertEquals("2026-05-01T10:00:00+03:00", item.createdAt)
        assertEquals("2026-06-01T10:00:00+03:00", item.validTo)
    }

    @Test
    fun `map leaves price blank when olx price is missing`() {
        val item = MyAdvertItemMapper.map(
            OlxAdvert(
                id = 43,
                title = "Phone",
                status = OlxAdvertStatus.Limited,
                url = "",
                primaryImageUrl = null,
                price = null,
                createdAt = "",
                validTo = "",
            ),
        )

        assertEquals("", item.priceFormatted)
        assertEquals(false, item.canOpen)
    }

    @Test
    fun `status mapping supports known olx lifecycle values`() {
        assertEquals(OlxAdvertStatus.New, OlxAdvertStatus.from("new"))
        assertEquals(OlxAdvertStatus.Active, OlxAdvertStatus.from("active"))
        assertEquals(OlxAdvertStatus.Limited, OlxAdvertStatus.from("limited"))
        assertEquals(OlxAdvertStatus.RemovedByUser, OlxAdvertStatus.from("removed_by_user"))
        assertEquals(OlxAdvertStatus.Outdated, OlxAdvertStatus.from("outdated"))
        assertEquals(OlxAdvertStatus.Unconfirmed, OlxAdvertStatus.from("unconfirmed"))
        assertEquals(OlxAdvertStatus.Unpaid, OlxAdvertStatus.from("unpaid"))
        assertEquals(OlxAdvertStatus.Moderated, OlxAdvertStatus.from("moderated"))
        assertEquals(OlxAdvertStatus.Blocked, OlxAdvertStatus.from("blocked"))
        assertEquals(OlxAdvertStatus.Disabled, OlxAdvertStatus.from("disabled"))
        assertEquals(OlxAdvertStatus.RemovedByModerator, OlxAdvertStatus.from("removed_by_moderator"))
        assertEquals(OlxAdvertStatus.Unknown, OlxAdvertStatus.from("unexpected"))
    }
}
