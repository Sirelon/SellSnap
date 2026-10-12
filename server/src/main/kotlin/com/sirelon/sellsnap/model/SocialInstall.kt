package com.sirelon.sellsnap.model

import java.time.Instant

/**
 * Android installs that the Play install referrer attributes to one post on one day, `social_installs/{day}_{code}`.
 * [day] is the `first_open` date from the Firebase Analytics export as `yyyymmdd`. A sync overwrites the document
 * with the count it read, so the sync is idempotent.
 */
data class SocialInstall(
    val accountId: String,
    val code: String,
    val day: String,
    val installs: Long,
    val syncedAt: Instant,
) {
    val id: String get() = "${day}_$code"
}
