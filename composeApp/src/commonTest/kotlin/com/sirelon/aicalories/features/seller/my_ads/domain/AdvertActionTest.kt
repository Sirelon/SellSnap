package com.sirelon.sellsnap.features.seller.my_ads.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the seller-facing state model (SIR-101). Two things must not drift: which of OLX's eleven
 * statuses lands in which state, and which actions each state offers. An action OLX would reject
 * must never be offered - guessing wrong means the seller taps a button and gets a server error.
 */
class AdvertActionTest {

    @Test
    fun `every OLX status maps to the state a seller would recognise`() {
        // Grouped only where the members differ in nothing the seller does. A status that moved
        // buckets by accident fails here, and one added to the enum fails the key assertion.
        val expected = mapOf(
            AdvertStatus.Active to AdvertState.Active,
            AdvertStatus.New to AdvertState.UnderReview,
            AdvertStatus.Disabled to AdvertState.UnderReview,
            AdvertStatus.Limited to AdvertState.NeedsPayment,
            AdvertStatus.Unpaid to AdvertState.NeedsPayment,
            AdvertStatus.Unconfirmed to AdvertState.NeedsConfirmation,
            AdvertStatus.Moderated to AdvertState.Rejected,
            AdvertStatus.Blocked to AdvertState.Rejected,
            AdvertStatus.RemovedByModerator to AdvertState.Rejected,
            AdvertStatus.RemovedByUser to AdvertState.Inactive,
            AdvertStatus.Outdated to AdvertState.Inactive,
            AdvertStatus.Unknown to AdvertState.Unknown,
        )

        assertEquals(AdvertStatus.entries.toSet(), expected.keys)
        for ((status, state) in expected) {
            assertEquals(state, status.state, "status $status")
        }
    }

    @Test
    fun `the action set per state is pinned`() {
        val expected = mapOf(
            AdvertState.Active to listOf(AdvertAction.Edit, AdvertAction.Extend, AdvertAction.Deactivate),
            AdvertState.Inactive to listOf(AdvertAction.Edit, AdvertAction.Reactivate, AdvertAction.Delete),
            AdvertState.UnderReview to listOf(AdvertAction.Edit, AdvertAction.Delete),
            AdvertState.NeedsPayment to listOf(AdvertAction.Edit, AdvertAction.Delete),
            AdvertState.NeedsConfirmation to listOf(AdvertAction.Edit, AdvertAction.Delete),
            AdvertState.Rejected to listOf(AdvertAction.Edit, AdvertAction.Delete),
            AdvertState.Unknown to emptyList(),
        )

        // A state added to the enum has to be given an action set here before this passes.
        assertEquals(AdvertState.entries.toSet(), expected.keys)

        for (status in AdvertStatus.entries) {
            assertEquals(
                expected.getValue(status.state),
                availableActions(status, supportsExtendCommand = true),
                "status $status is ${status.state}",
            )
        }
    }

    @Test
    fun `only an active listing differs by market, and only by extend`() {
        // `extend` is the single market difference anywhere in the API - the specs annotate it as
        // unavailable in UA and PT.
        for (status in AdvertStatus.entries) {
            val withExtend = availableActions(status, supportsExtendCommand = true)
            val withoutExtend = availableActions(status, supportsExtendCommand = false)
            assertEquals(
                if (status.state == AdvertState.Active) listOf(AdvertAction.Extend) else emptyList(),
                withExtend - withoutExtend.toSet(),
                "status $status",
            )
        }
    }

    @Test
    fun `deactivate is offered only where OLX accepts it, and delete only where it does not`() {
        // The two documented rules the whole table rests on: `deactivate` needs the advert to be
        // active, `DELETE` needs it not to be.
        for (status in AdvertStatus.entries) {
            val actions = availableActions(status, supportsExtendCommand = true)
            if (status.isLive) {
                assertTrue(AdvertAction.Deactivate in actions, "$status is live, so it can be taken down")
                assertFalse(AdvertAction.Delete in actions, "$status is live, so OLX refuses a delete")
            } else {
                assertFalse(AdvertAction.Deactivate in actions, "$status is not live, so OLX refuses a deactivate")
            }
        }

        // Only `active` is live - notably not `limited`, which is awaiting payment, not published.
        assertTrue(AdvertStatus.Active.isLive)
        assertFalse(AdvertStatus.Limited.isLive)
    }

    @Test
    fun `an unrecognised status offers nothing, because it might be an active advert`() {
        // Deleting an active advert is the one case OLX documents as refused, and it is not
        // recoverable, so an unknown string gets no buttons at all.
        for (supportsExtend in listOf(true, false)) {
            assertEquals(emptyList(), availableActions(AdvertStatus.Unknown, supportsExtendCommand = supportsExtend))
        }
    }
    @Test
    fun `editing is offered wherever OLX has not said otherwise`() {
        // `PUT adverts/{id}` carries no documented status restriction, and OLX's own web UI lets a
        // seller edit a listing that is under review. Restricting Edit to active listings was this
        // app's invention, and it withheld editing from the one listing a seller most wants to fix
        // - a rejected one, where OLX has emailed them what to change.
        for (status in AdvertStatus.entries) {
            val actions = availableActions(status, supportsExtendCommand = true)
            if (status.state == AdvertState.Unknown) {
                assertFalse(AdvertAction.Edit in actions, "$status could be anything, so offer nothing")
            } else {
                assertTrue(AdvertAction.Edit in actions, "$status has no documented reason to block an edit")
            }
        }
    }

}
