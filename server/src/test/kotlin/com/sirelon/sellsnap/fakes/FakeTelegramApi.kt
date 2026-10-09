package com.sirelon.sellsnap.fakes

import com.sirelon.sellsnap.telegram.Chat
import com.sirelon.sellsnap.telegram.InlineKeyboardMarkup
import com.sirelon.sellsnap.telegram.Message
import com.sirelon.sellsnap.telegram.ReplyMarkup
import com.sirelon.sellsnap.telegram.TelegramApi
import com.sirelon.sellsnap.telegram.TelegramFile
import com.sirelon.sellsnap.telegram.TelegramFailure

/** Records every call in [calls]; [failNext] queues failures per method name. */
class FakeTelegramApi : TelegramApi {
    sealed interface Call {
        val method: String

        class Sent(
            override val method: String,
            val chatId: Long,
            val messageId: Long,
            val text: String?,
            val markup: ReplyMarkup?,
            val replyToMessageId: Long? = null,
        ) : Call

        class Edited(
            override val method: String,
            val chatId: Long,
            val messageId: Long,
            val text: String?,
            val markup: InlineKeyboardMarkup?,
        ) : Call

        class Answered(val callbackQueryId: String, val text: String?) : Call {
            override val method = "answerCallbackQuery"
        }

        class Deleted(val chatId: Long, val messageId: Long) : Call {
            override val method = "deleteMessage"
        }
    }

    val calls = mutableListOf<Call>()

    /** File bytes by `file_path`, served by [downloadFile]. */
    val files = mutableMapOf<String, ByteArray>()

    private val failures = mutableMapOf<String, ArrayDeque<TelegramFailure>>()
    private var nextMessageId = 1000L

    fun failNext(method: String, vararg failure: TelegramFailure) {
        failures.getOrPut(method) { ArrayDeque() }.addAll(failure)
    }

    inline fun <reified T : Call> callsOf(): List<T> = calls.filterIsInstance<T>()

    private fun maybeFail(method: String) {
        failures[method]?.removeFirstOrNull()?.let { throw it }
    }

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyMarkup: ReplyMarkup?,
        replyToMessageId: Long?,
    ): Message {
        maybeFail("sendMessage")
        val id = nextMessageId++
        calls += Call.Sent("sendMessage", chatId, id, text, replyMarkup, replyToMessageId)
        return Message(messageId = id, chat = Chat(chatId), text = text)
    }

    override suspend fun sendPhoto(chatId: Long, photoUrl: String, caption: String?, replyMarkup: ReplyMarkup?): Message {
        maybeFail("sendPhoto")
        val id = nextMessageId++
        calls += Call.Sent("sendPhoto", chatId, id, caption, replyMarkup)
        return Message(messageId = id, chat = Chat(chatId), caption = caption)
    }

    override suspend fun editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup?,
    ) {
        maybeFail("editMessageText")
        calls += Call.Edited("editMessageText", chatId, messageId, text, replyMarkup)
    }

    override suspend fun editMessageCaption(
        chatId: Long,
        messageId: Long,
        caption: String,
        replyMarkup: InlineKeyboardMarkup?,
    ) {
        maybeFail("editMessageCaption")
        calls += Call.Edited("editMessageCaption", chatId, messageId, caption, replyMarkup)
    }

    override suspend fun editMessageReplyMarkup(chatId: Long, messageId: Long, replyMarkup: InlineKeyboardMarkup?) {
        maybeFail("editMessageReplyMarkup")
        calls += Call.Edited("editMessageReplyMarkup", chatId, messageId, null, replyMarkup)
    }

    override suspend fun answerCallbackQuery(callbackQueryId: String, text: String?) {
        maybeFail("answerCallbackQuery")
        calls += Call.Answered(callbackQueryId, text)
    }

    override suspend fun deleteMessage(chatId: Long, messageId: Long) {
        maybeFail("deleteMessage")
        calls += Call.Deleted(chatId, messageId)
    }

    override suspend fun getFile(fileId: String): TelegramFile {
        maybeFail("getFile")
        return TelegramFile(fileId = fileId, filePath = "photos/$fileId.jpg")
    }

    override suspend fun downloadFile(filePath: String): ByteArray {
        maybeFail("downloadFile")
        return files[filePath] ?: throw TelegramFailure("File not found")
    }
}
