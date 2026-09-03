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
 * Which actions OLX will actually accept, keyed on the seller-facing [AdvertState] rather than on
 * OLX's eleven status strings. An action that cannot succeed must not be offered - guessing wrong
 * means the seller taps a button and gets a server error.
 *
 * | State | Actions |
 * | -- | -- |
 * | `Active` | Edit, Extend (where the market allows), Deactivate |
 * | `Inactive` | Reactivate, Delete |
 * | `UnderReview` | Delete |
 * | `NeedsPayment` | Delete |
 * | `NeedsConfirmation` | Delete |
 * | `Rejected` | Delete |
 * | `Unknown` | nothing |
 *
 * Two documented rules generate the whole table. `deactivate` requires the advert to be `active`
 * (OLX answers "Ad has to be active" otherwise), and `DELETE` requires it NOT to be. Every state
 * except `Active` is one OLX defines as not visible to buyers, so Delete is offered for all of
 * them - including listings OLX rejected. Refusing there was this app's own invention and only
 * left sellers holding listings they wanted gone.
 *
 * `Unknown` is the exception: an unrecognised status string could be an active advert, and a
 * delete on an active advert is the one case OLX documents as refused.
 *
 * Reactivation is offered only for `Inactive`, where OLX's lifecycle text says an advert "can be
 * reactivated". Notably NOT for `NeedsPayment`: the docs say to purchase a packet and then send
 * `activate`, but the purchase is the precondition and `POST adverts/{id}/packets` spends the
 * seller's OLX balance, which is not going behind a button. A seller who pays on OLX gets it
 * activated there.
 *
 * [supportsExtendCommand] comes from
 * [com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry.supportsExtendCommand]; the specs
 * annotate `extend` itself as "not available in UA, PT".
 *
 * OLX's `finish` command is deliberately not offered. On an inactive listing it does nothing a
 * seller can perceive, and next to Delete it reads as a second, unexplained kind of removal. The
 * endpoint stays in the data layer; the button does not.
 *
 * [AdvertAction.Edit] is offered only on `Active`. `PUT adverts/{id}` is not documented as
 * status-restricted, so editing a listing that has not gone up yet is plausibly useful, but it is
 * unverified against a real advert and a half-working edit is worse than none - see SIR-99.
 */
fun availableActions(status: AdvertStatus, supportsExtendCommand: Boolean): List<AdvertAction> =
    when (status.state) {
        AdvertState.Active -> buildList {
            add(AdvertAction.Edit)
            if (supportsExtendCommand) add(AdvertAction.Extend)
            add(AdvertAction.Deactivate)
        }

        AdvertState.Inactive -> listOf(AdvertAction.Reactivate, AdvertAction.Delete)

        AdvertState.UnderReview,
        AdvertState.NeedsPayment,
        AdvertState.NeedsConfirmation,
        AdvertState.Rejected -> listOf(AdvertAction.Delete)

        AdvertState.Unknown -> emptyList()
    }
