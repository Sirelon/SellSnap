package com.sirelon.sellsnap.features.seller.my_ads.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the SIR-101 status-to-action mapping. An action that cannot succeed must
 * never be offered - guessing wrong means the seller taps a button and gets a server error - so
 * every branch is asserted, and [AdvertStatus.entries] is iterated so a newly added status
 * defaults to "no action" instead of silently inheriting whatever branch it happens to fall into.
 */
class AdvertActionTest {

    @Test
    fun `availableActions offers edit extend and deactivate for an active listing in a market that supports extend`() {
        assertEquals(
            listOf(AdvertAction.Edit, AdvertAction.Extend, AdvertAction.Deactivate),
            availableActions(AdvertStatus.Active, supportsExtendCommand = true),
        )
    }

    @Test
    fun `availableActions drops extend for an active listing in Ukraine and Portugal, where OLX rejects the command`() {
        assertEquals(
            listOf(AdvertAction.Edit, AdvertAction.Deactivate),
            availableActions(AdvertStatus.Active, supportsExtendCommand = false),
        )
    }

    @Test
    fun `a Limited listing is not live, so it is never offered deactivate`() {
        // OLX's documented lifecycle lands a posted advert in either `new` or `limited`, where
        // `limited` requires purchasing a packet before it goes up. It is awaiting payment, not
        // published - so `deactivate` would come back as "Ad has to be active", and a delete must
        // not waste a deactivate on it first.
        assertFalse(AdvertStatus.Limited.isLive)
        assertTrue(AdvertStatus.Active.isLive)

        for (supportsExtend in listOf(true, false)) {
            val actions = availableActions(AdvertStatus.Limited, supportsExtendCommand = supportsExtend)
            assertEquals(listOf(AdvertAction.Delete), actions)
        }
    }

    @Test
    fun `availableActions offers reactivate and delete for a taken-down listing regardless of extend support`() {
        val expected = listOf(AdvertAction.Reactivate, AdvertAction.Delete)

        for (supportsExtend in listOf(true, false)) {
            assertEquals(expected, availableActions(AdvertStatus.RemovedByUser, supportsExtendCommand = supportsExtend))
            assertEquals(expected, availableActions(AdvertStatus.Outdated, supportsExtendCommand = supportsExtend))
        }
    }

    @Test
    fun `an advert still in review can be deleted, because OLX only refuses to delete an active one`() {
        // Previously the sheet offered nothing at all for these, which left a seller who posted
        // something by mistake with no way to take it back. `DELETE adverts/{id}` requires only
        // that the advert not be `active`.
        for (status in listOf(AdvertStatus.New, AdvertStatus.Moderated, AdvertStatus.Unconfirmed, AdvertStatus.Unpaid)) {
            for (supportsExtend in listOf(true, false)) {
                assertEquals(
                    listOf(AdvertAction.Delete),
                    availableActions(status, supportsExtendCommand = supportsExtend),
                    "status $status is not active, so OLX accepts a delete",
                )
            }
        }
    }

    @Test
    fun `availableActions offers nothing for a status OLX controls, for every AdvertStatus value`() {
        // Blocked, RemovedByModerator and Disabled are OLX's decisions and nothing this app sends
        // would be accepted; Unknown means the app does not know what it is looking at. Iterating
        // every enum entry means a status added later and left out of the branches above fails
        // this test instead of silently offering a doomed action.
        val mapped = setOf(
            AdvertStatus.Active,
            AdvertStatus.Limited,
            AdvertStatus.RemovedByUser,
            AdvertStatus.Outdated,
            AdvertStatus.New,
            AdvertStatus.Moderated,
            AdvertStatus.Unconfirmed,
            AdvertStatus.Unpaid,
        )

        for (status in AdvertStatus.entries) {
            if (status in mapped) continue
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
