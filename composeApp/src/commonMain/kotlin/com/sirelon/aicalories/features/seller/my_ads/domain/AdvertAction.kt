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
 * OLX publishes no per-status definitions and no transition table: the spec's `Advert.status`
 * enum lists four values while its prose names eight more, and there is no status filter to
 * enumerate them. So each cell below is marked with what it actually rests on. `[doc]` is stated
 * in OLX's documentation, `[inf]` is inferred from a documented rule about a different subject,
 * and `[unk]` is not known.
 *
 * | Status | Edit | Extend | Deactivate | Reactivate | Delete |
 * | -- | -- | -- | -- | -- | -- |
 * | `active` | yes | yes, per market `[doc]` | yes `[doc]` | - | via deactivate `[doc]` |
 * | `limited` | - | - | no `[doc]` | no, see below | yes `[inf]` |
 * | `new` | - | - | no `[doc]` | no `[doc]` | yes `[inf]` |
 * | `moderated` | - | - | no `[doc]` | - | yes `[inf]` |
 * | `outdated` | - | - | - | yes `[doc]` | yes `[doc]` |
 * | `removed_by_user` | - | - | - | yes `[doc]` | yes `[doc]` |
 * | `unconfirmed` | - | - | no `[doc]` | - | yes `[inf]` |
 * | `unpaid` | - | - | no `[doc]` | - | yes `[inf]` |
 * | `blocked` | - | - | - | - | - |
 * | `removed_by_moderator` | - | - | - | - | - |
 * | `disabled` | - | - | - | - | - `[unk]` |
 * | `Unknown` | - | - | - | - | - |
 *
 * The two documented rules everything else hangs off: `deactivate` requires the advert to be
 * `active` (OLX answers "Ad has to be active" otherwise), and `DELETE` requires it NOT to be
 * `active`. Every `[inf]` delete is that second rule applied to a status that is plainly not
 * active. `[doc]` reactivation comes from the lifecycle text: an expired advert "can be
 * reactivated", and a manually deactivated one "can be reactivated or permanently deleted".
 *
 * `limited` deliberately does NOT offer reactivate, even though the docs say to "purchase a
 * packet for the advert or category and then trigger the activate command". The purchase is the
 * precondition and this app cannot make it - `POST adverts/{id}/packets` spends the seller's OLX
 * balance, which is not something to put behind a button labelled Publish. A seller who buys the
 * packet on OLX gets it activated there. So the state is explained instead, pointing at OLX.
 *
 * `blocked` and `removed_by_moderator` are OLX's own decisions. `disabled` is `[unk]`: it is not
 * in the documented enum, the prose groups it with the moderation states, and a real account
 * reported it for a listing that was live and editable on OLX's site. Reading it as "inactive"
 * would let Delete through, and deleting a live listing is not recoverable - so it offers nothing
 * and the sheet asks OLX for the reason instead. See SPIKE-SIR-99.
 *
 * [supportsExtendCommand] comes from
 * [com.sirelon.sellsnap.features.seller.auth.domain.OlxCountry.supportsExtendCommand]; `extend`
 * is documented as unavailable in Ukraine and Portugal.
 *
 * OLX's `finish` command is deliberately not offered. On an inactive listing it does nothing a
 * seller can perceive - the listing is already down, and "finish" next to "delete" reads as a
 * second, unexplained kind of removal. The endpoint stays in the data layer; the button does not.
 *
 * [AdvertAction.Edit] is offered only on active listings. `PUT adverts/{id}` is not documented as
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

        // Not active, so OLX accepts a delete - but nothing else this app could send. The state
        // itself is explained alongside, so the single button is not the whole answer.
        status == AdvertStatus.New ||
            status == AdvertStatus.Moderated ||
            status == AdvertStatus.Limited ||
            status == AdvertStatus.Unconfirmed ||
            status == AdvertStatus.Unpaid -> listOf(AdvertAction.Delete)

        // Blocked and RemovedByModerator are OLX's own decisions, and Unknown means the app does
        // not recognise what OLX sent.
        //
        // `Disabled` is here for a different reason: nobody knows what it means. It is not in the
        // spec's documented `Advert.status` enum at all, the prose lumps it in with the moderation
        // states, and yet a real account has reported it for a listing that was live and editable
        // on OLX's own site. Offering Delete on that reading would risk permanently removing a
        // live listing on a guess, so this stays empty and the copy sends the seller to OLX
        // instead of claiming to know why. Worth settling with one captured payload.
        else -> emptyList()
    }
