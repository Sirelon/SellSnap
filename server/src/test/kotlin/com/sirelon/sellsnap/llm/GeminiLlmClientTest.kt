@file:OptIn(ExperimentalCoroutinesApi::class)

package com.sirelon.sellsnap.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Field names follow https://ai.google.dev/api/generate-content (GenerationConfig, ThinkingConfig,
 * TextResponseFormat, FinishReason, BlockReason, UsageMetadata) and https://ai.google.dev/gemini-api/docs/thinking,
 * fetched 2026-10-10. The 503 "high demand" body is the live answer seen the same day.
 */
class GeminiLlmClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun gemini(
        retryDelays: List<Duration> = emptyList(),
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): GeminiLlmClient {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return GeminiLlmClient(HttpClient(engine) { expectSuccess = false }, KEY, retryDelays = retryDelays)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun HttpRequestData.bodyJson(): JsonObject =
        Json.parseToJsonElement((body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    private val request = LlmRequest(model = "gemini-3.8-flash", system = "Be brief.", user = "Say hi.")

    private fun answer(
        text: String = "Hi!",
        finishReason: String = "STOP",
        usage: String = """{"promptTokenCount":19,"candidatesTokenCount":30,"thoughtsTokenCount":247,"totalTokenCount":296}""",
    ) = """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(text)}}],"role":"model"},"finishReason":"$finishReason","index":0}],"usageMetadata":$usage}"""

    @Test
    fun sendsTheKeyInTheHeaderAndNeverInTheUrl() = runTest {
        val client = gemini { json(answer()) }

        client.complete(request)

        val sent = requests.single()
        assertEquals(KEY, sent.headers["x-goog-api-key"])
        assertFalse(KEY in sent.url.toString())
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", sent.url.toString().replace("%3A", ":"))
        assertTrue(sent.url.parameters.isEmpty())
    }

    @Test
    fun sendsTheSystemInstructionThinkingLevelAndTokenCap() = runTest {
        val client = gemini { json(answer()) }

        client.complete(request.copyWith(maxOutputTokens = 1500, thinkingLevel = "medium"))

        val body = requests.single().bodyJson()
        assertEquals("Be brief.", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val content = body["contents"]!!.jsonArray.single().jsonObject
        assertEquals("user", content["role"]!!.jsonPrimitive.content)
        assertEquals("Say hi.", content["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val config = body["generationConfig"]!!.jsonObject
        assertEquals(1500, config["maxOutputTokens"]!!.jsonPrimitive.int)
        assertEquals("medium", config["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
        assertNull(config["responseFormat"])
    }

    @Test
    fun defaultsAreTwoThousandTokensAndLowThinking() = runTest {
        val client = gemini { json(answer()) }

        client.complete(request)

        val config = requests.single().bodyJson()["generationConfig"]!!.jsonObject
        assertEquals(2000, config["maxOutputTokens"]!!.jsonPrimitive.int)
        assertEquals("low", config["thinkingConfig"]!!.jsonObject["thinkingLevel"]!!.jsonPrimitive.content)
    }

    @Test
    fun sendsTheResponseSchemaWithTheJsonMimeType() = runTest {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { putJsonObject("text") { put("type", "string") } }
        }
        val client = gemini { json(answer("""{"text":"hello"}""")) }

        val result = client.complete(request.copyWith(responseSchema = schema))

        val format = requests.single().bodyJson()["generationConfig"]!!.jsonObject["responseFormat"]!!.jsonObject["text"]!!.jsonObject
        assertEquals("APPLICATION_JSON", format["mimeType"]!!.jsonPrimitive.content)
        assertEquals(schema, format["schema"])
        assertEquals("""{"text":"hello"}""", result.text)
    }

    @Test
    fun parsesTextFinishReasonAndUsage() = runTest {
        val client = gemini { json(answer("Hello there")) }

        val result = client.complete(request)

        assertEquals("Hello there", result.text)
        assertEquals("STOP", result.finishReason)
        assertTrue(result.isComplete)
        assertEquals("gemini-3.8-flash", result.usage.model)
        assertEquals(19, result.usage.promptTokens)
        assertEquals(30, result.usage.outputTokens)
        assertEquals(247, result.usage.thoughtsTokens)
    }

    @Test
    fun missingThoughtsCountIsZeroAndThoughtPartsAreLeftOut() = runTest {
        val client = gemini {
            json(
                """{"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"Answer","thoughtSignature":"abc"}]},"finishReason":"STOP"}],
                    "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2}}""",
            )
        }

        val result = client.complete(request)

        assertEquals("Answer", result.text)
        assertEquals(0, result.usage.thoughtsTokens)
    }

    @Test
    fun maxTokensIsExposedAsAnIncompleteResult() = runTest {
        val client = gemini { json(answer("cut o", finishReason = "MAX_TOKENS")) }

        val result = client.complete(request)

        assertEquals("MAX_TOKENS", result.finishReason)
        assertFalse(result.isComplete)
        assertEquals("cut o", result.text)
    }

    @Test
    fun highDemandAnswersAreRetriedThenTransient() = runTest {
        val client = gemini(retryDelays = DELAYS) {
            json(
                """{"error":{"code":503,"message":"This model is currently experiencing high demand.","status":"UNAVAILABLE"}}""",
                HttpStatusCode.ServiceUnavailable,
            )
        }

        val failure = assertFailsWith<LlmFailure.Transient> { client.complete(request) }

        assertEquals(4, requests.size)
        assertEquals(7_000, currentTime)
        assertEquals("Gemini temporary error (HTTP 503)", failure.message)
    }

    @Test
    fun aRetryThatSucceedsReturnsTheResult() = runTest {
        var calls = 0
        val client = gemini(retryDelays = DELAYS) {
            if (++calls < 3) json("{}", HttpStatusCode.ServiceUnavailable) else json(answer("Finally"))
        }

        assertEquals("Finally", client.complete(request).text)
        assertEquals(3, requests.size)
    }

    @Test
    fun networkErrorsAreRetriedAndBecomeTransient() = runTest {
        val client = gemini(retryDelays = DELAYS) { throw IOException("connect failed: https://x/?key=$KEY") }

        val failure = assertFailsWith<LlmFailure.Transient> { client.complete(request) }

        assertEquals(4, requests.size)
        assertEquals("Gemini request failed (network error)", failure.message)
    }

    @Test
    fun serverErrorsAreTransient() = runTest {
        val client = gemini { json("{}", HttpStatusCode.InternalServerError) }

        assertIs<LlmFailure.Transient>(assertFailsWith<LlmFailure> { client.complete(request) })
    }

    @Test
    fun rateLimitIsNotRetried() = runTest {
        val client = gemini(retryDelays = DELAYS) {
            json("""{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}""", HttpStatusCode.TooManyRequests)
        }

        val failure = assertFailsWith<LlmFailure.RateLimited> { client.complete(request) }

        assertEquals(1, requests.size)
        assertEquals("Gemini rate limit reached (HTTP 429)", failure.message)
    }

    @Test
    fun otherClientErrorsArePermanentAndNotRetried() = runTest {
        val client = gemini(retryDelays = DELAYS) { json("{}", HttpStatusCode.Forbidden) }

        val failure = assertFailsWith<LlmFailure.Permanent> { client.complete(request) }

        assertEquals(1, requests.size)
        assertEquals(403, failure.httpStatus)
    }

    @Test
    fun aBlockedPromptIsRefused() = runTest {
        val client = gemini(retryDelays = DELAYS) { json("""{"promptFeedback":{"blockReason":"SAFETY"}}""") }

        val failure = assertFailsWith<LlmFailure.Refused> { client.complete(request) }

        assertEquals(1, requests.size)
        assertEquals("Gemini blocked the prompt (SAFETY)", failure.message)
    }

    @Test
    fun blockingFinishReasonsAreRefused() = runTest {
        for (reason in listOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")) {
            val client = gemini { json("""{"candidates":[{"finishReason":"$reason"}]}""") }

            val failure = assertFailsWith<LlmFailure.Refused>(reason) { client.complete(request) }

            assertEquals("Gemini blocked the answer ($reason)", failure.message)
        }
    }

    @Test
    fun anUnreadableOrEmptyAnswerIsTransient() = runTest {
        for (body in listOf("not json", "{}", """{"candidates":[]}""")) {
            val client = gemini { json(body) }

            assertIs<LlmFailure.Transient>(assertFailsWith<LlmFailure> { client.complete(request) }, body)
        }
    }

    @Test
    fun noFailureTextContainsTheKey() = runTest {
        val echoed = """{"error":{"message":"API key $KEY is invalid","key":"$KEY"}}"""
        val cases = listOf<MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>(
            { json(echoed, HttpStatusCode.BadRequest) },
            { json(echoed, HttpStatusCode.TooManyRequests) },
            { json(echoed, HttpStatusCode.ServiceUnavailable) },
            { json("""{"promptFeedback":{"blockReason":"$KEY"}}""") },
            { json(echoed) },
            { throw IOException("boom $KEY") },
        )
        for (handler in cases) {
            val failure = assertFailsWith<LlmFailure> { gemini(handler = handler).complete(request) }

            assertFalse(KEY in failure.message.orEmpty(), failure.message)
            assertFalse(KEY in failure.stackTraceToString(), failure.message)
        }
    }

    @Test
    fun aModelNameWithPathCharactersIsRejected() = runTest {
        val client = gemini { json(answer()) }

        assertFailsWith<IllegalArgumentException> { client.complete(request.copyWith(model = "x/../y?key=1")) }
        assertTrue(requests.isEmpty())
    }

    private fun LlmRequest.copyWith(
        model: String = this.model,
        maxOutputTokens: Int = this.maxOutputTokens,
        thinkingLevel: String = this.thinkingLevel,
        responseSchema: JsonObject? = this.responseSchema,
    ) = LlmRequest(model, system, user, responseSchema, maxOutputTokens, thinkingLevel)

    private companion object {
        const val KEY = "AIzaSyD-test-key-0123456789abcdefghijklmn"
        val DELAYS = listOf(1.seconds, 2.seconds, 4.seconds)
    }
}
