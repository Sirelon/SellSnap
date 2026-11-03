package com.sirelon.sellsnap.features.seller.my_ads.domain

import com.sirelon.sellsnap.features.seller.messages.domain.OlxThread

/**
 * Unread messages per advert id: a thread's `advert_id` is the My Ads row's id, so threads about
 * one advert add up. Adverts with nothing unread are left out, so a row looks its id up and shows
 * a marker only when it finds one.
 */
internal fun List<OlxThread>.unreadByAdvert(): Map<Long, Int> =
    filter { it.unreadCount > 0 }
        .groupBy { it.advertId }
        .mapValues { (_, threads) -> threads.sumOf { it.unreadCount } }
