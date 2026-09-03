package com.sirelon.sellsnap.features.seller.auth.domain

/**
 * Current totals from `GET adverts/{id}/statistics`. There is no time series in the response,
 * only these three counters as of now.
 */
data class OlxAdvertStatistics(
    val advertViews: Int,
    val phoneViews: Int,
    val usersObserving: Int,
) {
    /** No activity at all - "too early to tell" rather than an error or a diagnosis. */
    val isEmpty: Boolean get() = advertViews == 0 && phoneViews == 0 && usersObserving == 0
}
