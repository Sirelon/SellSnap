package com.sirelon.sellsnap.features.seller.my_ads.domain

/**
 * Buckets for the lifecycle analytics (SIR-106). Every outcome number is banded before it leaves
 * the device so no individual sale is reconstructable from the event stream - a raw achieved
 * price plus a raw suggestion would identify one listing.
 */
internal object AdvertAnalyticsBuckets {

    /** Bands around zero, so "sold at the suggested price" is its own answer. */
    fun priceDelta(percent: Int): String = when {
        percent <= -50 -> "lte_-50"
        percent <= -25 -> "-49_-25"
        percent <= -10 -> "-24_-10"
        percent < 0 -> "-9_-1"
        percent == 0 -> "0"
        percent <= 10 -> "1_10"
        percent <= 25 -> "11_25"
        percent <= 50 -> "26_50"
        else -> "gt_50"
    }

    /** "Sells in about a week" needs the week boundary to be a bucket edge. */
    fun daysLive(days: Int): String = when {
        days <= 0 -> "0"
        days <= 2 -> "1_2"
        days <= 7 -> "3_7"
        days <= 14 -> "8_14"
        days <= 30 -> "15_30"
        else -> "gt_30"
    }

    /** Low views is a different diagnosis from lots of views, so the low end is finer-grained. */
    fun advertViews(views: Int): String = when {
        views <= 0 -> "0"
        views <= 10 -> "1_10"
        views <= 50 -> "11_50"
        views <= 200 -> "51_200"
        views <= 1000 -> "201_1000"
        else -> "gt_1000"
    }
}
