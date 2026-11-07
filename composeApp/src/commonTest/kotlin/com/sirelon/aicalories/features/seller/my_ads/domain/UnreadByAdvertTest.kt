package com.sirelon.sellsnap.features.seller.my_ads.domain

import com.sirelon.sellsnap.features.seller.messages.domain.OlxThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnreadByAdvertTest {

    private fun thread(id: Long, advertId: Long, unread: Int) =
        OlxThread(id = id, advertId = advertId, interlocutorId = 900, unreadCount = unread, createdAt = "2026-01-02 10:11:12")

    @Test
    fun `threads about one advert add up`() {
        val threads = listOf(thread(1, 10, 2), thread(2, 10, 3), thread(3, 11, 1))

        assertEquals(mapOf(10L to 5, 11L to 1), threads.unreadByAdvert())
    }

    @Test
    fun `an advert with nothing unread is absent`() {
        val threads = listOf(thread(1, 10, 0), thread(2, 10, 0), thread(3, 11, 4))

        assertEquals(mapOf(11L to 4), threads.unreadByAdvert())
    }

    @Test
    fun `no threads means no markers`() {
        assertTrue(emptyList<OlxThread>().unreadByAdvert().isEmpty())
    }
}
