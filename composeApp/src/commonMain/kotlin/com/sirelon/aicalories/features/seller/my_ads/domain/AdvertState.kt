package com.sirelon.sellsnap.features.seller.my_ads.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

/**
 * What a seller sees their listing as. The single input to badge labels, badge colours,
 * explanations and [availableActions].
 *
 * OLX's API reports eleven statuses and this app used to mirror all eleven: eleven badges, nine
 * explanation paragraphs, a per-status action list. That is the API's model, not a seller's - it
 * drew distinctions like automatic-versus-manual checking that change nothing anyone would do.
 * These seven are the words a seller already uses on OLX, where their own cabinet files listings
 * under Активні / Неактивні / Відхилені with "на модерації" and "потрібна оплата" alongside.
 *
 * A group exists only where its members differ in what the seller does about them. Every status
 * in a group has the same action set, which is why collapsing them cannot offer an action OLX
 * would refuse.
 */
enum class AdvertState {
    /** Visible to buyers. The only state where taking down or editing is possible. */
    Active,

    /** OLX is looking at it. Nothing to do but wait - or delete it. */
    UnderReview,

    /** Buyers will see it once the seller pays, which happens on OLX. */
    NeedsPayment,

    /** OLX is waiting on the seller to confirm something, on OLX. */
    NeedsConfirmation,

    /** OLX said no. The reason came by email; deleting or reposting is all that is left. */
    Rejected,

    /** Down, whether the seller took it down or it expired. Can go back up. */
    Inactive,

    /** A status string this app does not recognise. */
    Unknown,
}

/**
 * OLX's status vocabulary, grouped. Definitions are OLX's own, from the `Advert statuses` section
 * of the UA and PT specs - the PL spec omits them, so do not use it when revisiting this.
 *
 * | OLX status | OLX's definition | State | Why grouped here |
 * | -- | -- | -- | -- |
 * | `active` | visible on OLX | Active | Alone: the only state that can be taken down or edited. |
 * | `new` | fresh advert before activation and moderation | UnderReview | OLX is looking at it. |
 * | `disabled` | disabled by moderation, offer blocked and waiting for verification | UnderReview | Also OLX looking at it, just for longer. Fast or slow, by machine or by person, the seller waits either way. |
 * | `limited` | advert exceeded limit of free adverts in selected category | NeedsPayment | Buyers see it once a packet is bought. |
 * | `unpaid` | waiting for payment | NeedsPayment | Same remedy, same place to do it. |
 * | `unconfirmed` | waiting for confirmation | NeedsConfirmation | Separate from payment: telling someone to pay when they need to click a confirmation is wrong information. |
 * | `moderated` | negative moderation result | Rejected | OLX said no. |
 * | `blocked` | blocked by moderation | Rejected | OLX said no. |
 * | `removed_by_moderator` | removed by moderator | Rejected | OLX said no. Its own cabinet files all three under Відхилені. |
 * | `removed_by_user` | manually removed by user | Inactive | Put back up or delete. |
 * | `outdated` | advert reached expiration date | Inactive | Same two actions. That it expired is already on the row as the expiry line, so a second badge for it is noise. |
 * | `Unknown` | not a status OLX documents | Unknown | Could be an active advert, so nothing is offered. |
 *
 * Exhaustive with no `else`: a status added to [AdvertStatus] stops compiling until someone
 * decides which state it belongs to.
 */
val AdvertStatus.state: AdvertState
    get() = when (this) {
        AdvertStatus.Active -> AdvertState.Active
        AdvertStatus.New,
        AdvertStatus.Disabled -> AdvertState.UnderReview
        AdvertStatus.Limited,
        AdvertStatus.Unpaid -> AdvertState.NeedsPayment
        AdvertStatus.Unconfirmed -> AdvertState.NeedsConfirmation
        AdvertStatus.Moderated,
        AdvertStatus.Blocked,
        AdvertStatus.RemovedByModerator -> AdvertState.Rejected
        AdvertStatus.RemovedByUser,
        AdvertStatus.Outdated -> AdvertState.Inactive
        AdvertStatus.Unknown -> AdvertState.Unknown
    }
