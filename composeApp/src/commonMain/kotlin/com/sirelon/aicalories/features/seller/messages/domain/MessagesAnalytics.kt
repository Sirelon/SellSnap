package com.sirelon.sellsnap.features.seller.messages.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

/**
 * Codes for the Messages analytics (SIR-92). Counts and times are banded before they leave the
 * device, and a listing's state is a code: no event carries message text, a title, an id or
 * anything else a buyer or a listing could be recognised by.
 */
internal object MessagesAnalyticsBuckets {

    /** Threads, unread messages and messages in a conversation: the low end is the fine-grained one. */
    fun count(count: Int): String = when {
        count <= 0 -> "0"
        count == 1 -> "1"
        count <= 5 -> "2_5"
        count <= 10 -> "6_10"
        count <= 25 -> "11_25"
        count <= 50 -> "26_50"
        else -> "gt_50"
    }

    /** Whole seconds from opening a conversation to a tap in it. */
    fun secondsSinceOpen(seconds: Long): String = when {
        seconds <= 5 -> "0_5"
        seconds <= 30 -> "6_30"
        seconds <= 120 -> "31_120"
        else -> "gt_120"
    }

    /**
     * The advert's state as the seller sees it: `deleted` (OLX answered 404), `unknown` (not
     * known yet, or OLX sent a status this app does not know), otherwise OLX's own status code.
     */
    fun listingState(status: AdvertStatus?, isDeleted: Boolean): String = when {
        isDeleted -> "deleted"
        status == null -> "unknown"
        else -> when (status) {
            AdvertStatus.New -> "new"
            AdvertStatus.Active -> "active"
            AdvertStatus.Limited -> "limited"
            AdvertStatus.RemovedByUser -> "removed_by_user"
            AdvertStatus.Outdated -> "outdated"
            AdvertStatus.Unconfirmed -> "unconfirmed"
            AdvertStatus.Unpaid -> "unpaid"
            AdvertStatus.Moderated -> "moderated"
            AdvertStatus.Blocked -> "blocked"
            AdvertStatus.Disabled -> "disabled"
            AdvertStatus.RemovedByModerator -> "removed_by_moderator"
            AdvertStatus.Unknown -> "unknown"
        }
    }
}
