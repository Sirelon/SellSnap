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

/**
 * Live on OLX: visible to buyers. The only state from which OLX accepts a `deactivate`, and so
 * also the only one where a delete has to deactivate first.
 *
 * `Limited` is deliberately NOT live. OLX's documented lifecycle has a posted advert land in
 * either `new` or `limited`, where `limited` "requires purchasing a packet to activate" - it is
 * awaiting payment, not published. Treating it as live offered a Deactivate that OLX would refuse
 * with "Ad has to be active", and would have made a delete send a pointless deactivate first.
 */
val AdvertStatus.isLive: Boolean
    get() = this == AdvertStatus.Active

/**
 * Which actions OLX will actually accept for [status]. An action that cannot succeed must not be
 * offered - guessing wrong here means the seller taps a button and gets a server error, so this
 * mapping is the substance of SIR-101 rather than a detail of it.
 *
 * [supportsExtendCommand] comes from
 * [com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry.supportsExtendCommand]; `extend`
 * is documented as unavailable in Ukraine and Portugal.
 *
 * Delete is offered for any advert that is neither active nor under OLX's control. `DELETE
 * adverts/{id}` requires only that the advert "MUST NOT be in `active` status", so a listing
 * still in moderation, awaiting a packet, or awaiting confirmation can be removed - which is the
 * one thing a seller wants for an advert they posted by mistake, and previously the sheet offered
 * them nothing at all.
 *
 * OLX's `finish` command is deliberately not offered. On an inactive listing it does nothing a
 * seller can perceive - the listing is already down, and "finish" next to "delete" reads as a
 * second, unexplained kind of removal. The endpoint stays in the data layer; the button does not.
 *
 * [AdvertAction.Edit] is offered only on active listings. `PUT adverts/{id}` is not documented as
 * status-restricted, but whether it is accepted on an inactive or expired advert is unverified
 * against a real advert, and "edit then reactivate" failing halfway is worse than not offering
 * it - see SIR-99.
 */
fun availableActions(status: AdvertStatus, supportsExtendCommand: Boolean): List<AdvertAction> =
    when {
        status.isLive -> buildList {
            add(AdvertAction.Edit)
            if (supportsExtendCommand) add(AdvertAction.Extend)
            add(AdvertAction.Deactivate)
        }

        status == AdvertStatus.RemovedByUser || status == AdvertStatus.Outdated ->
            listOf(AdvertAction.Reactivate, AdvertAction.Delete)

        // Not active, so OLX accepts a delete - but nothing else this app could send. The state
        // itself is explained alongside, so the single button is not the whole answer.
        status == AdvertStatus.New ||
            status == AdvertStatus.Moderated ||
            status == AdvertStatus.Limited ||
            status == AdvertStatus.Unconfirmed ||
            status == AdvertStatus.Unpaid -> listOf(AdvertAction.Delete)

        // Blocked, RemovedByModerator, Disabled: OLX controls these, and Unknown means the app
        // does not know what it is looking at. Explained rather than made to look actionable.
        else -> emptyList()
    }
