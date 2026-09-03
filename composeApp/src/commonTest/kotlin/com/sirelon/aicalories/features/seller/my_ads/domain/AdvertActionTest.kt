package com.sirelon.sellsnap.features.seller.my_ads.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for the SIR-101 status-to-action mapping. An action that cannot succeed must
 * never be offered - guessing wrong means the seller taps a button and gets a server error - so
 * every branch is asserted, and [AdvertStatus.entries] is iterated so a newly added status
 * defaults to "no action" instead of silently inheriting whatever branch it happens to fall into.
 */
class AdvertActionTest {

    @Test
    fun `availableActions offers edit extend and deactivate for a live listing in a market that supports extend`() {
        val expected = listOf(AdvertAction.Edit, AdvertAction.Extend, AdvertAction.Deactivate)

        assertEquals(expected, availableActions(AdvertStatus.Active, supportsExtendCommand = true))
        assertEquals(expected, availableActions(AdvertStatus.Limited, supportsExtendCommand = true))
    }

    @Test
    fun `availableActions drops extend for a live listing in Ukraine and Portugal, where OLX rejects the command`() {
        val expected = listOf(AdvertAction.Edit, AdvertAction.Deactivate)

        assertEquals(expected, availableActions(AdvertStatus.Active, supportsExtendCommand = false))
        assertEquals(expected, availableActions(AdvertStatus.Limited, supportsExtendCommand = false))
    }

    @Test
    fun `availableActions offers reactivate finish and delete for a taken-down listing regardless of extend support`() {
        val expected = listOf(AdvertAction.Reactivate, AdvertAction.Finish, AdvertAction.Delete)

        for (supportsExtend in listOf(true, false)) {
            assertEquals(expected, availableActions(AdvertStatus.RemovedByUser, supportsExtendCommand = supportsExtend))
            assertEquals(expected, availableActions(AdvertStatus.Outdated, supportsExtendCommand = supportsExtend))
        }
    }

    @Test
    fun `availableActions offers nothing for every status OLX does not document as actionable, for every AdvertStatus value`() {
        // Nothing this app sends would be accepted for a moderator-controlled or blocked advert,
        // so the mapping must explain the state rather than make it look actionable. Iterating
        // every enum entry means a status added later and left out of the two actionable branches
        // above fails this test instead of silently offering a doomed action.
        val actionableStatuses = setOf(
            AdvertStatus.Active,
            AdvertStatus.Limited,
            AdvertStatus.RemovedByUser,
            AdvertStatus.Outdated,
        )

        for (status in AdvertStatus.entries) {
            if (status in actionableStatuses) continue
            for (supportsExtend in listOf(true, false)) {
                assertEquals(
                    emptyList(),
                    availableActions(status, supportsExtendCommand = supportsExtend),
                    "status $status must not offer an action that cannot succeed",
                )
            }
        }
    }
}
