package com.sirelon.sellsnap.features.seller.messages.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MessagesAnalyticsBucketsTest {

    @Test
    fun `counts fall on their bucket edges`() {
        val expected = mapOf(
            -1 to "0", 0 to "0", 1 to "1", 2 to "2_5", 5 to "2_5", 6 to "6_10", 10 to "6_10",
            11 to "11_25", 25 to "11_25", 26 to "26_50", 50 to "26_50", 51 to "gt_50",
        )
        expected.forEach { (count, bucket) -> assertEquals(bucket, MessagesAnalyticsBuckets.count(count), "count $count") }
    }

    @Test
    fun `seconds since open fall on their bucket edges`() {
        val expected = mapOf(0L to "0_5", 5L to "0_5", 6L to "6_30", 30L to "6_30", 31L to "31_120", 120L to "31_120", 121L to "gt_120")
        expected.forEach { (seconds, bucket) ->
            assertEquals(bucket, MessagesAnalyticsBuckets.secondsSinceOpen(seconds), "$seconds s")
        }
    }

    @Test
    fun `every status has a snake_case code and deleted and unknown are their own states`() {
        AdvertStatus.entries.forEach { status ->
            val code = MessagesAnalyticsBuckets.listingState(status, isDeleted = false)
            assertEquals(code, code.lowercase())
            assertEquals(false, code.contains(' '))
        }
        assertEquals("deleted", MessagesAnalyticsBuckets.listingState(AdvertStatus.Active, isDeleted = true))
        assertEquals("unknown", MessagesAnalyticsBuckets.listingState(null, isDeleted = false))
        assertEquals("removed_by_moderator", MessagesAnalyticsBuckets.listingState(AdvertStatus.RemovedByModerator, false))
    }
}
