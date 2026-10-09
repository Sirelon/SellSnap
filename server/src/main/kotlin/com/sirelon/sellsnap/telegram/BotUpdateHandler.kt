package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.threads.redact
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration

/**
 * Entry point for one Telegram update. Only the owner counts; everything else is dropped without
 * a reply. An update id is processed once. Any failure becomes a one-line redacted reply, and
 * [handle] still returns normally so the route answers 200 and Telegram does not redeliver.
 */
class BotUpdateHandler(
    private val store: SocialStore,
    private val telegram: TelegramApi,
    private val approvals: ApprovalActions,
    private val commands: Commands,
    private val clock: Clock,
    private val ownerTelegramId: Long,
) {
    suspend fun handle(update: Update) {
        val sender = update.callbackQuery?.from?.id ?: update.message?.from?.id
        if (sender != ownerTelegramId) return
        try {
            if (!store.claimUpdate(update.updateId, clock.instant().plus(UPDATE_TTL))) return
            val query = update.callbackQuery
            val message = update.message
            when {
                query != null -> approvals.onCallback(query)
                message != null -> onMessage(message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError(update, e)
        }
    }

    private suspend fun onMessage(message: Message) {
        val text = message.text ?: message.caption ?: return
        when {
            text.trimStart().startsWith("/") -> commands.handle(message, text)
            message.text != null && approvals.onReply(message) -> Unit
            message.text != null -> telegram.sendMessage(message.chat.id, BotText.PLAIN_TEXT_HINT)
        }
    }

    private suspend fun reportError(update: Update, error: Exception) {
        val detail = redact(error.message ?: error::class.simpleName.orEmpty())
            .replace(Regex("\\s+"), " ")
            .take(MAX_ERROR_LENGTH)
        val chatId = update.message?.chat?.id ?: update.callbackQuery?.message?.chat?.id ?: ownerTelegramId
        update.callbackQuery?.let { runQuietly { telegram.answerCallbackQuery(it.id, BotText.error(detail)) } }
        runQuietly { telegram.sendMessage(chatId, BotText.error(detail)) }
    }

    private suspend fun runQuietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The error reply is best effort; the update was already given up on.
        }
    }

    private companion object {
        val UPDATE_TTL: Duration = Duration.ofDays(7)
        const val MAX_ERROR_LENGTH = 200
    }
}
