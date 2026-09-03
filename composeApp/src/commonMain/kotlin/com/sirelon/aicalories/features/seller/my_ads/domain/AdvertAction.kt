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
 * Live on OLX: visible to buyers, and the only states from which OLX accepts a `deactivate` - so
 * also the states where a delete has to deactivate first.
 *
 * Kept next to [availableActions] because they branch on the same pair. A status OLX later starts
 * treating as live has to change in exactly one place.
 */
val AdvertStatus.isLive: Boolean
    get() = this == AdvertStatus.Active || this == AdvertStatus.Limited

/**
 * Which actions OLX will actually accept for [status]. An action that cannot succeed must not be
 * offered - guessing wrong here means the seller taps a button and gets a server error, so this
 * mapping is the substance of SIR-101 rather than a detail of it.
 *
 * [supportsExtendCommand] comes from
 * [com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry.supportsExtendCommand]; `extend`
 * is documented as unavailable in Ukraine and Portugal.
 *
 * OLX's `finish` command is deliberately not offered. On an inactive listing it does nothing a
 * seller can perceive - the listing is already down, and "finish" next to "delete" reads as a
 * second, unexplained kind of removal. The endpoint stays in the data layer; the button does not.
 *
 * [AdvertAction.Edit] is offered only on live listings. `PUT adverts/{id}` is not documented as
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

        // Moderator-controlled, or blocked on OLX's side. Nothing this app sends would be
        // accepted, so the state is explained instead of being made to look actionable.
        else -> emptyList()
    }
