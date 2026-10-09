package com.sirelon.sellsnap.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Telegram Bot API port. Text is sent as plain text. Callers keep to Telegram's limits:
 * `callback_data` up to 64 bytes, captions up to 1024 characters, messages up to 4096.
 * Failures throw [TelegramFailure] with a fixed message that never contains the bot token.
 */
interface TelegramApi {
    suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyMarkup: ReplyMarkup? = null,
        replyToMessageId: Long? = null,
    ): Message

    suspend fun sendPhoto(chatId: Long, photoUrl: String, caption: String?, replyMarkup: ReplyMarkup? = null): Message

    suspend fun editMessageText(chatId: Long, messageId: Long, text: String, replyMarkup: InlineKeyboardMarkup? = null)

    suspend fun editMessageCaption(
        chatId: Long,
        messageId: Long,
        caption: String,
        replyMarkup: InlineKeyboardMarkup? = null,
    )

    /** A null [replyMarkup] removes the keyboard. */
    suspend fun editMessageReplyMarkup(chatId: Long, messageId: Long, replyMarkup: InlineKeyboardMarkup?)

    suspend fun answerCallbackQuery(callbackQueryId: String, text: String? = null)

    suspend fun deleteMessage(chatId: Long, messageId: Long)

    suspend fun getFile(fileId: String): TelegramFile

    suspend fun downloadFile(filePath: String): ByteArray
}

/** [retryAfterSeconds] is set on a 429 reply. */
class TelegramFailure(message: String, val retryAfterSeconds: Int? = null) : Exception(message)

@Serializable
data class Update(
    @SerialName("update_id") val updateId: Long,
    val message: Message? = null,
    @SerialName("callback_query") val callbackQuery: CallbackQuery? = null,
)

@Serializable
data class Message(
    @SerialName("message_id") val messageId: Long,
    val chat: Chat,
    val from: User? = null,
    val text: String? = null,
    val caption: String? = null,
    val photo: List<PhotoSize> = emptyList(),
    @SerialName("reply_to_message") val replyToMessage: Message? = null,
)

@Serializable
data class Chat(val id: Long)

@Serializable
data class User(val id: Long, @SerialName("is_bot") val isBot: Boolean = false)

@Serializable
data class PhotoSize(
    @SerialName("file_id") val fileId: String,
    val width: Int,
    val height: Int,
)

@Serializable
data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message? = null,
    val data: String? = null,
)

@Serializable
data class TelegramFile(
    @SerialName("file_id") val fileId: String,
    @SerialName("file_path") val filePath: String? = null,
)

/** Markup accepted by `sendMessage` and `sendPhoto`. */
sealed interface ReplyMarkup

@Serializable
data class InlineKeyboardMarkup(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>,
) : ReplyMarkup

@Serializable
data class InlineKeyboardButton(
    val text: String,
    @SerialName("callback_data") val callbackData: String,
)

/** Opens a reply prompt; the wire form is `{"force_reply": true}`. */
@Serializable
data class ForceReply(@SerialName("force_reply") val forceReply: Boolean) : ReplyMarkup
