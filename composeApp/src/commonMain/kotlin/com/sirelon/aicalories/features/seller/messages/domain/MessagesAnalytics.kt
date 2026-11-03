package com.sirelon.sellsnap.features.seller.messages.domain

import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus

/**
 * Where a reply's text came from, the `source` of `message_sent`: typed by the seller, an AI
 * suggestion sent with one tap, or a suggestion put into the field and then sent from it (SIR-96).
 */
enum class MessageSource(val code: String) {
    Typed("typed"),
    Suggestion("suggestion"),
    EditedSuggestion("edited_suggestion"),
}

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

    /**
     * Milliseconds from tapping "Suggest replies" to the answer: `lt_1000`, `1000_2000`,
     * `2000_4000`, `4000_8000` or `gt_8000`.
     */
    fun latencyMs(millis: Long): String = when {
        millis < 1_000 -> "lt_1000"
        millis < 2_000 -> "1000_2000"
        millis < 4_000 -> "2000_4000"
        millis < 8_000 -> "4000_8000"
        else -> "gt_8000"
    }

    /**
     * How far the asking price is above the seller's lowest price, in percent of that lowest
     * price: `0` (at or below it), `1_10`, `11_25`, `26_50` or `gt_50`. Null when there is no
     * asking price or no usable floor, so the param is left out.
     */
    fun floorGap(floor: Long, asking: Long?): String? {
        if (asking == null || floor <= 0) return null
        val percent = ((asking - floor) * 100) / floor
        return when {
            percent <= 0 -> "0"
            percent <= 10 -> "1_10"
            percent <= 25 -> "11_25"
            percent <= 50 -> "26_50"
            else -> "gt_50"
        }
    }
}
