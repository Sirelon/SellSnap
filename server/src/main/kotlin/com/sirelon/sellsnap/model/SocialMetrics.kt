package com.sirelon.sellsnap.model

import java.time.Instant

/**
 * One post's insight counts on one local day. [day] is the account's local date as `yyyyMMdd`;
 * the document id is `{postId}_{day}`. [views] is null when the `views` metric was unavailable.
 */
data class PostMetrics(
    val postId: String,
    val accountId: String,
    val day: String,
    val at: Instant,
    val views: Long?,
    val likes: Long,
    val replies: Long,
    val reposts: Long,
    val quotes: Long,
) {
    val id: String get() = "${postId}_$day"
}

/**
 * An account's follower count on one local day; the document id is `{accountId}_{day}`. Its
 * existence marks the account-day as collected. [followersCount] is null when the follower
 * call failed permanently, so the day is still marked and the failure is not retried every tick.
 */
data class AccountMetrics(
    val accountId: String,
    val day: String,
    val at: Instant,
    val followersCount: Long?,
) {
    val id: String get() = "${accountId}_$day"
}
