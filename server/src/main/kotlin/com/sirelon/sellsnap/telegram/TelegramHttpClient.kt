package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.threads.redact
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

/**
 * A Ktor client for the Telegram Bot API: Java engine, `expectSuccess = false`, so a failed
 * response becomes a [TelegramFailure] here and never a Ktor exception whose message carries the
 * request URL, which holds the bot token.
 */
fun telegramHttpClient(engine: HttpClientEngine = Java.create()): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10.seconds.inWholeMilliseconds
        requestTimeoutMillis = 30.seconds.inWholeMilliseconds
    }
}

/**
 * [TelegramApi] over `api.telegram.org`. Requests are JSON bodies and text is plain, with no
 * `parse_mode`. A 429 reply with `parameters.retry_after` is waited out once, when the wait is at
 * most [maxRetryWaitSeconds]; a longer wait fails at once so a webhook request never hangs.
 *
 * Every failure carries a fixed message built from the method name and the HTTP status; no Ktor
 * exception message and no response text leaves this class, and each message also passes [redact].
 */
class TelegramHttpClient(
    private val client: HttpClient,
    botToken: String,
    private val baseUrl: String = "https://api.telegram.org",
    private val maxRetryWaitSeconds: Int = 30,
) : TelegramApi {
    private val methodBase = "${baseUrl.trimEnd('/')}/bot$botToken"
    private val fileBase = "${baseUrl.trimEnd('/')}/file/bot$botToken"

    override suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyMarkup: ReplyMarkup?,
        replyToMessageId: Long?,
    ): Message = decodeMessage(
        call("sendMessage") {
            put("chat_id", chatId)
            put("text", text)
            putMarkup(replyMarkup)
            if (replyToMessageId != null) {
                put("reply_parameters", buildJsonObject { put("message_id", replyToMessageId) })
            }
        },
        "sendMessage",
    )

    override suspend fun sendPhoto(chatId: Long, photoUrl: String, caption: String?, replyMarkup: ReplyMarkup?): Message =
        decodeMessage(
            call("sendPhoto") {
                put("chat_id", chatId)
                put("photo", photoUrl)
                if (caption != null) put("caption", caption)
                putMarkup(replyMarkup)
            },
            "sendPhoto",
        )

    override suspend fun editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup?,
    ) {
        call("editMessageText") {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("text", text)
            putMarkup(replyMarkup)
        }
    }

    override suspend fun editMessageCaption(
        chatId: Long,
        messageId: Long,
        caption: String,
        replyMarkup: InlineKeyboardMarkup?,
    ) {
        call("editMessageCaption") {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("caption", caption)
            putMarkup(replyMarkup)
        }
    }

    override suspend fun editMessageReplyMarkup(chatId: Long, messageId: Long, replyMarkup: InlineKeyboardMarkup?) {
        call("editMessageReplyMarkup") {
            put("chat_id", chatId)
            put("message_id", messageId)
            // Omitting reply_markup removes the keyboard.
            putMarkup(replyMarkup)
        }
    }

    override suspend fun answerCallbackQuery(callbackQueryId: String, text: String?) {
        call("answerCallbackQuery") {
            put("callback_query_id", callbackQueryId)
            if (text != null) put("text", text)
        }
    }

    override suspend fun deleteMessage(chatId: Long, messageId: Long) {
        call("deleteMessage") {
            put("chat_id", chatId)
            put("message_id", messageId)
        }
    }

    override suspend fun getFile(fileId: String): TelegramFile {
        val result = call("getFile") { put("file_id", fileId) }
        return try {
            JSON.decodeFromJsonElement(TelegramFile.serializer(), result ?: throw unreadable("getFile"))
        } catch (e: Exception) {
            throw unreadable("getFile")
        }
    }

    override suspend fun downloadFile(filePath: String): ByteArray {
        val bytes = try {
            val response = client.get("$fileBase/$filePath")
            if (!response.status.isSuccess()) throw failure("downloadFile", response.status.value)
            response.bodyAsBytes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TelegramFailure) {
            throw e
        } catch (e: Exception) {
            throw networkFailure("downloadFile")
        }
        return bytes
    }

    /** Posts [fill] as a JSON body to [method]; returns `result` of a successful reply. */
    private suspend fun call(method: String, fill: JsonObjectBuilder.() -> Unit): JsonElement? {
        val body = buildJsonObject(fill).toString()
        var retried = false
        while (true) {
            val reply = try {
                val response = client.post("$methodBase/$method") {
                    setBody(TextContent(body, ContentType.Application.Json))
                }
                Reply(response.status.value, response.status.isSuccess(), response.bodyAsText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The exception message may hold the request URL with the bot token, so it is dropped.
                throw networkFailure(method)
            }
            val parsed = runCatching { JSON.parseToJsonElement(reply.text).jsonObject }.getOrNull()
            if (reply.success && parsed?.get("ok").asBoolean() == true) return parsed?.get("result")

            val retryAfter = (parsed?.get("parameters") as? JsonObject)?.get("retry_after").asInt()
            if (reply.status == 429 && retryAfter != null && !retried && retryAfter <= maxRetryWaitSeconds) {
                retried = true
                delay(retryAfter.coerceAtLeast(1).seconds)
                continue
            }
            throw failure(method, reply.status, retryAfter.takeIf { reply.status == 429 })
        }
    }

    private class Reply(val status: Int, val success: Boolean, val text: String)

    private fun decodeMessage(result: JsonElement?, method: String): Message = try {
        JSON.decodeFromJsonElement(Message.serializer(), result ?: throw unreadable(method))
    } catch (e: Exception) {
        throw unreadable(method)
    }

    private fun JsonObjectBuilder.putMarkup(markup: ReplyMarkup?) {
        when (markup) {
            null -> Unit
            is InlineKeyboardMarkup -> put("reply_markup", JSON.encodeToJsonElement(InlineKeyboardMarkup.serializer(), markup))
            is ForceReply -> put("reply_markup", JSON.encodeToJsonElement(ForceReply.serializer(), markup))
        }
    }

    private fun failure(method: String, status: Int, retryAfter: Int? = null) =
        TelegramFailure(redact("Telegram rejected $method (HTTP $status)"), retryAfter)

    private fun networkFailure(method: String) = TelegramFailure(redact("Telegram request failed for $method (network error)"))

    private fun unreadable(method: String) = TelegramFailure(redact("Telegram returned an unreadable reply for $method"))

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

private fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull
