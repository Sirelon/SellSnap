package com.sirelon.sellsnap.model

import java.time.Instant
import java.time.ZoneId

enum class AccountMode { Approval, Auto }

data class SocialAccount(
    val id: String,
    val handle: String,
    val language: String,
    val country: String,
    val timezone: String,
    /** Leading characters of every post code of this account; unique across accounts. */
    val codePrefix: String,
    val mode: AccountMode = AccountMode.Approval,
    val approvalStreak: Int = 0,
    val paused: Boolean = false,
    val pausedReason: String? = null,
    val threadsUserId: String? = null,
    val token: String? = null,
    val tokenExpiresAt: Instant? = null,
    val tokenRefreshedAt: Instant? = null,
    /** End of the private-profile permission grant (§3.6); null when the token carries none. */
    val permissionsExpireAt: Instant? = null,
    /** Last sequence number handed out to a post code of this account. */
    val postSeq: Int = 0,
    val publishedCount: Int = 0,
    /** False keeps the account out of the weekly batch; a manual batch run can still name it. */
    val batchEnabled: Boolean = true,
) {
    val zone: ZoneId get() = ZoneId.of(timezone)

    /** True when a new post of this account starts `approved` instead of `pending`: auto mode, and the post needs no attention. */
    fun autoApproves(needsAttention: Boolean): Boolean = mode == AccountMode.Auto && !needsAttention
}
