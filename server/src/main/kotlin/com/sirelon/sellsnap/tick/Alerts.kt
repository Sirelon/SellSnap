package com.sirelon.sellsnap.tick

import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.telegram.BotText
import com.sirelon.sellsnap.telegram.TelegramApi
import com.sirelon.sellsnap.threads.AlertRequest
import com.sirelon.sellsnap.threads.AlertType
import com.sirelon.sellsnap.threads.redact
import java.time.Clock

/** Sends owner alerts through Telegram, one short English message each. */
class Alerts(
    private val store: SocialStore,
    private val telegram: TelegramApi,
    private val clock: Clock,
    private val ownerChatId: Long,
) {
    /**
     * Sends [request] unless the same alert (type, account, UTC day) was already recorded. Use for
     * alerts nothing has recorded yet. Returns true when the message went out.
     */
    suspend fun sendOnce(request: AlertRequest): Boolean {
        val now = clock.instant()
        val event = SocialEvent(
            id = request.eventId(now),
            accountId = request.accountId,
            type = EventType.Alert,
            at = now,
            postId = request.postId,
            detail = request.type.key,
        )
        if (!store.recordEvent(event)) return false
        send(request)
        return true
    }

    /** Sends [request] without a dedup check; the caller already recorded its event. */
    suspend fun send(request: AlertRequest) {
        telegram.sendMessage(ownerChatId, render(request))
    }

    companion object {
        private const val MAX_DETAIL_LENGTH = 200

        /** The owner message for [request]. The detail is redacted, flattened to one line and capped. */
        fun render(request: AlertRequest): String {
            val detail = cleanDetail(request.detail)
            val account = request.accountId
            return when (request.type) {
                AlertType.PublishFailed -> BotText.alertPublishFailed(account, request.postId, detail)
                AlertType.PublishStalled -> BotText.alertPublishStalled(account, request.postId, detail)
                AlertType.PostNeedsAttention -> BotText.alertPostNeedsAttention(account, request.postId)
                AlertType.TokenExpiry -> BotText.alertTokenExpiry(account, request.expiresAt)
                AlertType.PermissionsExpiry -> BotText.alertPermissionsExpiry(account, request.expiresAt)
                AlertType.TokenRefreshFailed -> BotText.alertTokenRefreshFailed(account, detail)
                AlertType.TickFailed -> BotText.alertTickFailed(account, detail)
            }
        }

        private fun cleanDetail(detail: String?): String =
            redact(detail.orEmpty()).replace(Regex("\\s+"), " ").trim().trimEnd('.').take(MAX_DETAIL_LENGTH)
                .ifEmpty { BotText.NO_DETAIL }
    }
}
