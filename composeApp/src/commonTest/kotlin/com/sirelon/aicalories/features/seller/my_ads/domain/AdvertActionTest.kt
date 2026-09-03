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
    fun `a listing OLX blocked can still be deleted, because only an active advert refuses it`() {
        // Refusing here was this app's own invention, not OLX's rule - it left sellers holding
        // listings they wanted gone. OLX defines all three as not visible to buyers, and the only
        // documented constraint on DELETE is that the advert not be active.
        for (status in listOf(AdvertStatus.Blocked, AdvertStatus.RemovedByModerator, AdvertStatus.Disabled)) {
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
    fun `an unrecognised status offers nothing, because it might be an active advert`() {
        // The one case OLX documents as refused is deleting an active advert, and an unknown
        // status string could be exactly that. Guessing wrong is not recoverable.
        for (supportsExtend in listOf(true, false)) {
            assertEquals(emptyList(), availableActions(AdvertStatus.Unknown, supportsExtendCommand = supportsExtend))
        }
    }

    @Test
    fun `the whole status matrix is pinned, so no status changes behaviour unnoticed`() {
        // The table in availableActions' KDoc, as executable form. OLX documents no per-status
        // definitions and no transition table, so this is the only place the mapping is checked
        // as a whole rather than status by status - and any edit to it has to be deliberate.
        val expected = mapOf(
            AdvertStatus.Active to listOf(AdvertAction.Edit, AdvertAction.Extend, AdvertAction.Deactivate),
            AdvertStatus.Limited to listOf(AdvertAction.Delete),
            AdvertStatus.New to listOf(AdvertAction.Delete),
            AdvertStatus.Moderated to listOf(AdvertAction.Delete),
            AdvertStatus.Outdated to listOf(AdvertAction.Reactivate, AdvertAction.Delete),
            AdvertStatus.RemovedByUser to listOf(AdvertAction.Reactivate, AdvertAction.Delete),
            AdvertStatus.Unconfirmed to listOf(AdvertAction.Delete),
            AdvertStatus.Unpaid to listOf(AdvertAction.Delete),
            AdvertStatus.Blocked to listOf(AdvertAction.Delete),
            AdvertStatus.RemovedByModerator to listOf(AdvertAction.Delete),
            AdvertStatus.Disabled to listOf(AdvertAction.Delete),
            AdvertStatus.Unknown to emptyList(),
        )

        // Every status OLX can send has a row, so a value added to the enum fails here.
        assertEquals(AdvertStatus.entries.toSet(), expected.keys)

        for ((status, actions) in expected) {
            assertEquals(actions, availableActions(status, supportsExtendCommand = true), "status $status")
        }

        // Extend is the only cell that varies by market, and only for an active listing.
        for (status in AdvertStatus.entries) {
            val withoutExtend = availableActions(status, supportsExtendCommand = false)
            val difference = expected.getValue(status) - withoutExtend.toSet()
            assertEquals(
                if (status == AdvertStatus.Active) listOf(AdvertAction.Extend) else emptyList(),
                difference,
                "only an active listing may differ by market, and only by Extend - status $status",
            )
        }
    }
}
