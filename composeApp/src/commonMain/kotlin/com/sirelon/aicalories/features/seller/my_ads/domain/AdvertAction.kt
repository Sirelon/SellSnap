package com.sirelon.sellsnap.features.seller.my_ads.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

/** A lifecycle action a seller can take on one of their own OLX listings (SIR-101). */
enum class AdvertAction {
    /** Takes a live listing down. OLX requires an answer to "did it sell?" to accept this. */
    Deactivate,

    /** Puts an inactive or expired listing back up. `activate` on the OLX side. */
    Reactivate,

    /** Removes the listing from OLX permanently. Only accepted while the advert is inactive. */
    Delete,

    /** Pushes the expiry date out. Rejected by OLX in some markets. */
    Extend,

    /** Changes the listing's text or price and pushes it back to OLX. */
    Edit,
}

/** Visible to buyers - the only state OLX accepts a `deactivate` from, and so also the only one
 * where a delete has to deactivate first. */
val AdvertStatus.isLive: Boolean
    get() = state == AdvertState.Active

/**
 * Which actions to offer per [AdvertState], and where each one's justification comes from.
 *
 * **Documented by OLX.** `deactivate` requires the advert to be `active` - OLX answers "Ad has to
 * be active" otherwise. `DELETE` requires it NOT to be `active`. `extend` is annotated in the
 * specs as unavailable in UA and PT. The lifecycle text says an expired or seller-removed advert
 * "can be reactivated".
 *
 * **Inferred, not documented.** That Delete works for every non-active state. It follows from the
 * DELETE rule and OLX's own definitions - all of those states are ones OLX describes as not
 * visible to buyers - but no document lists it per status, and it is unverified against a real
 * advert of each kind. A wrong inference here costs one error message quoting OLX, not data.
 *
 * **Not restricted by OLX at all.** `PUT adverts/{id}` carries no documented status restriction,
 * and OLX's own web UI lets a seller edit a listing that is under review. Edit is therefore
 * offered everywhere except `Unknown`. An earlier version of this file restricted it to `Active`;
 * that was this app's invention and it made the app useless for exactly the listing a seller most
 * wants to fix - one OLX has just rejected, having emailed them what to change.
 *
 * | State | Actions |
 * | -- | -- |
 * | `Active` | Edit, Extend (where the market allows), Deactivate |
 * | `Inactive` | Edit, Reactivate, Delete |
 * | `UnderReview` | Edit, Delete |
 * | `NeedsPayment` | Edit, Delete |
 * | `NeedsConfirmation` | Edit, Delete |
 * | `Rejected` | Edit, Delete |
 * | `Unknown` | nothing |
 *
 * Deactivate stays `Active`-only for a product reason on top of OLX's rule: OLX requires an
 * answer to "did it sell?" before it will accept a deactivate, and that question is nonsense for
 * a listing buyers never saw. For those, withdrawing it means Delete, which is offered.
 *
 * `Unknown` gets nothing: an unrecognised status string could be an active advert, and deleting
 * an active advert is the one case OLX documents as refused - and it is not recoverable.
 *
 * Reactivate is offered only for `Inactive`, matching the lifecycle text. Notably NOT for
 * `NeedsPayment`: the docs say to purchase a packet and then send `activate`, but the purchase is
 * the precondition and `POST adverts/{id}/packets` spends the seller's OLX balance, which is not
 * going behind a button. A seller who pays on OLX gets it activated there.
 *
 * OLX's `finish` command is deliberately not offered - on an inactive listing it does nothing a
 * seller can perceive, and next to Delete it reads as a second, unexplained kind of removal.
 *
 * Anything inferred rather than documented is watched in production: `advert_action` logs
 * `result: rejected` with `from_status`, so a cell that OLX actually refuses shows up as a
 * pattern against one status instead of staying a guess.
 */
fun availableActions(status: AdvertStatus, supportsExtendCommand: Boolean): List<AdvertAction> =
    when (status.state) {
        AdvertState.Active -> buildList {
            add(AdvertAction.Edit)
            if (supportsExtendCommand) add(AdvertAction.Extend)
            add(AdvertAction.Deactivate)
        }

        AdvertState.Inactive -> listOf(AdvertAction.Edit, AdvertAction.Reactivate, AdvertAction.Delete)

        AdvertState.UnderReview,
        AdvertState.NeedsPayment,
        AdvertState.NeedsConfirmation,
        AdvertState.Rejected -> listOf(AdvertAction.Edit, AdvertAction.Delete)

        AdvertState.Unknown -> emptyList()
    }
