package com.sirelon.sellsnap.threads

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What an alert is about; [key] goes into the daily dedup event id. */
enum class AlertType(val key: String) {
    /** A post failed permanently and its account was paused. */
    PublishFailed("publish_failed"),

    /** A post failed transiently on three ticks in a row. */
    PublishStalled("publish_stalled"),

    /** A post's link reply failed permanently or went live without a recorded id; the post stays live and the account runs on. */
    ReplyFailed("reply_failed"),

    /** A post went live but its media id could not be recovered. */
    PostNeedsAttention("post_needs_attention"),

    TokenExpiry("token_expiry"),
    PermissionsExpiry("permissions_expiry"),
    TokenRefreshFailed("token_refresh_failed"),

    /** The tick threw for one account; the other accounts still ran. */
    TickFailed("tick_failed"),

    /** An insights call failed permanently or hit a rate limit; the account is never paused for it. */
    InsightsFailed("insights_failed"),

    /** A batch run stopped drafting an account (the model is busy or at its limit); the next run continues. */
    BatchDeferred("batch_deferred"),

    /** A batch started for an account but had not finished by 11:00 Kyiv time with no run holding its lease. */
    BatchStalled("batch_stalled"),

    /** The install attribution read failed; it belongs to no account, so [AlertRequest.accountId] is [AlertRequest.INSTALL_SYNC_ACCOUNT]. */
    InstallSyncFailed("install_sync_failed"),
}

/**
 * A request for the owner alert, as structured data: this layer sends nothing. [detail] has
 * passed [redact]. The Telegram layer renders the text.
 */
data class AlertRequest(
    val type: AlertType,
    val accountId: String,
    val postId: String? = null,
    val detail: String? = null,
    /** Token and permission alerts: when the thing expires. */
    val expiresAt: Instant? = null,
    /** Batch alerts: the ISO week of the batch. */
    val week: String? = null,
) {
    /** `alert_{type}_{account}_{yyyymmdd}`, the id that keeps one alert per type and account per day. */
    fun eventId(at: Instant): String = "alert_${type.key}_${accountId}_${DAY.format(at)}"

    companion object {
        /** The `accountId` of an alert that belongs to no account. */
        const val INSTALL_SYNC_ACCOUNT = "install-sync"

        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    }
}
