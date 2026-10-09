@file:OptIn(ExperimentalCoroutinesApi::class)

package com.sirelon.sellsnap.telegram

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixtures follow https://core.telegram.org/bots/api (the `{"ok":..,"result":..}` envelope,
 * `error_code`, `description` and `parameters.retry_after` on a 429), fetched 2026-10-09.
 */
class TelegramHttpClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): TelegramHttpClient {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return TelegramHttpClient(HttpClient(engine) { expectSuccess = false }, botToken = BOT_TOKEN)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.jsonBody(): JsonObject =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    private val messageReply = """{"ok":true,"result":{"message_id":77,"chat":{"id":5},"text":"hi","date":1}}"""
    private val tooMany = """{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 2","parameters":{"retry_after":2}}"""

    @Test
    fun sendMessageSendsPlainTextAsJsonWithoutParseMode() = runTest {
        val client = api { json(messageReply) }

        val message = client.sendMessage(5, "Hello <b>there</b>")

        assertEquals(77, message.messageId)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.telegram.org/bot$BOT_TOKEN/sendMessage", request.url.toString())
        val body = request.jsonBody()
        assertEquals("5", body.getValue("chat_id").jsonPrimitive.content)
        assertEquals("Hello <b>there</b>", body.getValue("text").jsonPrimitive.content)
        assertFalse("parse_mode" in body)
        assertFalse("reply_markup" in body)
    }

    @Test
    fun sendMessageEncodesAnInlineKeyboardAndAReplyTarget() = runTest {
        val client = api { json(messageReply) }
        val keyboard = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("Approve", "a:stt0001"))))

        client.sendMessage(5, "Hi", keyboard, replyToMessageId = 9)

        val body = requests.single().jsonBody()
        val button = body.getValue("reply_markup").jsonObject.getValue("inline_keyboard").toString()
        assertEquals("""[[{"text":"Approve","callback_data":"a:stt0001"}]]""", button)
        assertEquals("9", body.getValue("reply_parameters").jsonObject.getValue("message_id").jsonPrimitive.content)
    }

    @Test
    fun forceReplyIsEncodedAsForceReplyTrue() = runTest {
        val client = api { json(messageReply) }

        client.sendMessage(5, "Reply here", ForceReply(true))

        val markup = requests.single().jsonBody().getValue("reply_markup").jsonObject
        assertEquals(setOf("force_reply"), markup.keys)
        assertTrue(markup.getValue("force_reply").jsonPrimitive.boolean)
    }

    @Test
    fun sendPhotoPassesTheUrlAndTheCaption() = runTest {
        val client = api { json(messageReply) }

        client.sendPhoto(5, "https://images.test/a.jpg", "Caption")

        val request = requests.single()
        assertEquals("https://api.telegram.org/bot$BOT_TOKEN/sendPhoto", request.url.toString())
        val body = request.jsonBody()
        assertEquals("https://images.test/a.jpg", body.getValue("photo").jsonPrimitive.content)
        assertEquals("Caption", body.getValue("caption").jsonPrimitive.content)
        assertFalse("parse_mode" in body)
    }

    @Test
    fun editsAndAnswersUseTheirOwnMethods() = runTest {
        val client = api { json("""{"ok":true,"result":true}""") }

        client.editMessageText(5, 8, "New")
        client.editMessageCaption(5, 8, "Cap")
        client.editMessageReplyMarkup(5, 8, null)
        client.answerCallbackQuery("cb1", "Done")
        client.deleteMessage(5, 8)

        assertEquals(
            listOf("editMessageText", "editMessageCaption", "editMessageReplyMarkup", "answerCallbackQuery", "deleteMessage"),
            requests.map { it.url.encodedPath.substringAfterLast('/') },
        )
        assertFalse("reply_markup" in requests[2].jsonBody())
        assertEquals("cb1", requests[3].jsonBody().getValue("callback_query_id").jsonPrimitive.content)
        assertEquals("Done", requests[3].jsonBody().getValue("text").jsonPrimitive.content)
    }

    @Test
    fun getFileAndDownloadFileUseTheFilePath() = runTest {
        val client = api { request ->
            if (request.url.encodedPath.endsWith("/getFile")) {
                json("""{"ok":true,"result":{"file_id":"f1","file_path":"photos/file_1.jpg"}}""")
            } else {
                respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
            }
        }

        val file = client.getFile("f1")
        val bytes = client.downloadFile(file.filePath.orEmpty())

        assertEquals("photos/file_1.jpg", file.filePath)
        assertContentEquals(byteArrayOf(1, 2, 3), bytes)
        assertEquals("https://api.telegram.org/file/bot$BOT_TOKEN/photos/file_1.jpg", requests[1].url.toString())
        assertEquals(HttpMethod.Get, requests[1].method)
    }

    @Test
    fun aRateLimitIsWaitedOutOnceAndRetried() = runTest {
        var calls = 0
        val client = api { if (++calls == 1) json(tooMany, HttpStatusCode.TooManyRequests) else json(messageReply) }

        val message = client.sendMessage(5, "Hi")

        assertEquals(77, message.messageId)
        assertEquals(2, requests.size)
        assertEquals(2_000, currentTime)
    }

    @Test
    fun aSecondRateLimitFailsWithTheWaitAndTriesOnlyTwice() = runTest {
        val client = api { json(tooMany, HttpStatusCode.TooManyRequests) }

        val failure = assertFailsWith<TelegramFailure> { client.sendMessage(5, "Hi") }

        assertEquals(2, failure.retryAfterSeconds)
        assertEquals(2, requests.size)
    }

    @Test
    fun aLongWaitIsNotWaitedOut() = runTest {
        val long = """{"ok":false,"error_code":429,"description":"x","parameters":{"retry_after":600}}"""
        val client = api { json(long, HttpStatusCode.TooManyRequests) }

        val failure = assertFailsWith<TelegramFailure> { client.sendMessage(5, "Hi") }

        assertEquals(600, failure.retryAfterSeconds)
        assertEquals(1, requests.size)
        assertEquals(0, currentTime)
    }

    @Test
    fun otherErrorsFailWithAFixedMessageAndNoRetry() = runTest {
        val client = api {
            json("""{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}""", HttpStatusCode.BadRequest)
        }

        val failure = assertFailsWith<TelegramFailure> { client.sendMessage(5, "Hi") }

        assertEquals("Telegram rejected sendMessage (HTTP 400)", failure.message)
        assertNull(failure.retryAfterSeconds)
        assertEquals(1, requests.size)
    }

    @Test
    fun noFailurePathCarriesTheBotToken() = runTest {
        val failures = mutableListOf<TelegramFailure>()
        val bad = api { json("""{"ok":false,"error_code":401,"description":"Unauthorized"}""", HttpStatusCode.Unauthorized) }
        val garbled = api { json("<html>bad gateway</html>", HttpStatusCode.BadGateway) }
        val unreadable = api { json("""{"ok":true,"result":"nope"}""") }
        val broken = api { throw IOException("connect failed: https://api.telegram.org/bot$BOT_TOKEN/sendMessage") }
        val brokenDownload = api { throw IOException("connect failed: https://api.telegram.org/file/bot$BOT_TOKEN/x.jpg") }
        val missingFile = api { respond("", HttpStatusCode.NotFound) }

        for (client in listOf(bad, garbled, unreadable, broken)) {
            failures += assertFailsWith<TelegramFailure> { client.sendMessage(5, "Hi") }
        }
        failures += assertFailsWith<TelegramFailure> { bad.getFile("f") }
        failures += assertFailsWith<TelegramFailure> { brokenDownload.downloadFile("x.jpg") }
        failures += assertFailsWith<TelegramFailure> { missingFile.downloadFile("x.jpg") }

        assertEquals(7, failures.size)
        failures.forEach { assertFalse(BOT_TOKEN in it.message.orEmpty(), it.message) }
    }

    private companion object {
        const val BOT_TOKEN = "123456789:AAFakeTokenForTestsOnly_abcdefghij12345"
    }
}
