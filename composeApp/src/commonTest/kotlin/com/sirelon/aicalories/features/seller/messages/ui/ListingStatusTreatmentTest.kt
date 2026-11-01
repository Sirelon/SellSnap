package com.sirelon.sellsnap.features.seller.messages.ui

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The row and header treatment per listing status: chip, dimming, and what stays undimmed. */
class ListingStatusTreatmentTest {

    private enum class Treatment { Live, Waiting, Down, Unknown }

    private val expected = mapOf(
        AdvertStatus.Active to Treatment.Live,
        AdvertStatus.New to Treatment.Waiting,
        AdvertStatus.Disabled to Treatment.Waiting,
        AdvertStatus.Limited to Treatment.Waiting,
        AdvertStatus.Unpaid to Treatment.Waiting,
        AdvertStatus.Unconfirmed to Treatment.Waiting,
        AdvertStatus.Moderated to Treatment.Down,
        AdvertStatus.Blocked to Treatment.Down,
        AdvertStatus.RemovedByModerator to Treatment.Down,
        AdvertStatus.RemovedByUser to Treatment.Down,
        AdvertStatus.Outdated to Treatment.Down,
        AdvertStatus.Unknown to Treatment.Unknown,
    )

    @Test
    fun `every status is placed, so a new one cannot slip through untreated`() {
        assertEquals(AdvertStatus.entries.toSet(), expected.keys)
    }

    @Test
    fun `chip and dimming follow the status`() {
        for ((status, treatment) in expected) {
            assertEquals(
                treatment == Treatment.Waiting || treatment == Treatment.Down,
                status.showsStatusChip(),
                "chip for $status",
            )
            assertEquals(treatment == Treatment.Down, isListingDimmed(status, isDeleted = false), "dimmed for $status")
        }
    }

    @Test
    fun `a listing that is not known yet has no chip and is not dimmed`() {
        assertFalse(null.showsStatusChip())
        assertFalse(isListingDimmed(status = null, isDeleted = false))
    }

    @Test
    fun `a deleted listing is dimmed whatever status is left over`() {
        assertTrue(isListingDimmed(status = null, isDeleted = true))
        assertTrue(isListingDimmed(status = AdvertStatus.Active, isDeleted = true))
    }
}
