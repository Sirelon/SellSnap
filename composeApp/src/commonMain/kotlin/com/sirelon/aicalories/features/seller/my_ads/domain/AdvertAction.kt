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
 * offered - guessing wrong means the seller taps a button and gets a server error - so this
 * mapping is the substance of SIR-101 rather than a detail of it.
 *
 * OLX's own definitions, from the `Advert statuses` section of the UA and PT specs. The PL spec
 * omits them and lists only four values on the `Advert.status` enum, which is why an earlier pass
 * built on guesses; read a non-PL spec when this needs revisiting.
 *
 * | Status | OLX's definition | Actions |
 * | -- | -- | -- |
 * | `active` | visible on OLX | Edit, Extend (per market), Deactivate |
 * | `new` | fresh advert before activation and moderation | Delete |
 * | `limited` | exceeded limit of free adverts in selected category | Delete |
 * | `outdated` | advert reached expiration date | Reactivate, Delete |
 * | `removed_by_user` | manually removed by user | Reactivate, Delete |
 * | `unconfirmed` | waiting for confirmation | Delete |
 * | `unpaid` | waiting for payment | Delete |
 * | `moderated` | **negative moderation result** | Delete |
 * | `blocked` | blocked by moderation | Delete |
 * | `disabled` | disabled by moderation, offer blocked and waiting for verification | Delete |
 * | `removed_by_moderator` | removed by moderator | Delete |
 * | `Unknown` | not a status OLX documents | nothing |
 *
 * Two documented rules generate the whole table. `deactivate` requires the advert to be `active`
 * (OLX answers "Ad has to be active" otherwise), and `DELETE` requires it NOT to be. Every status
 * above except `active` is one OLX defines as not visible to buyers, so Delete is offered for all
 * of them - including the ones OLX blocked. Refusing there was this app's own invention and only
 * left sellers holding listings they wanted gone.
 *
 * `Unknown` is the exception: an unrecognised status string could be an active advert, and a
 * delete on an active advert is the one case OLX documents as refused.
 *
 * Reactivation is offered only where the lifecycle text says an advert "can be reactivated" -
 * expired, or taken down by the seller. Notably NOT for `limited`: the docs say to purchase a
 * packet and then send `activate`, but the purchase is the precondition and
 * `POST adverts/{id}/packets` spends the seller's OLX balance, which is not going behind a button
 * labelled Publish. A seller who buys the packet on OLX gets it activated there.
 *
 * [supportsExtendCommand] comes from
 * [com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry.supportsExtendCommand]; the specs
 * annotate `extend` itself as "not available in UA, PT".
 *
 * OLX's `finish` command is deliberately not offered. On an inactive listing it does nothing a
 * seller can perceive, and next to Delete it reads as a second, unexplained kind of removal. The
 * endpoint stays in the data layer; the button does not.
 *
 * [AdvertAction.Edit] is offered only on `active`. `PUT adverts/{id}` is not documented as
 * status-restricted, so editing a listing that has not gone up yet is plausibly useful, but it is
 * unverified against a real advert and a half-working edit is worse than none - see SIR-99.
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

        // Every remaining status is one OLX defines as not visible to buyers, and the only
        // documented constraint on DELETE is that the advert not be `active` - so removing it is
        // the seller's call, including for a listing OLX blocked or is verifying. Refusing was
        // this app's invention, not OLX's rule, and it left sellers stuck with listings they
        // wanted gone. The state is explained alongside, so the single button is not the whole
        // answer.
        status != AdvertStatus.Unknown -> listOf(AdvertAction.Delete)

        // `Unknown` means OLX sent a status string this app does not recognise. It could be
        // active, so nothing is offered - a delete on an active advert is the one case OLX
        // documents as refused, and guessing wrong here is not recoverable.
        else -> emptyList()
    }
