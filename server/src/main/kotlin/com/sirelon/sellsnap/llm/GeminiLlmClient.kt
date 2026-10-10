package com.sirelon.sellsnap.llm

import com.sirelon.sellsnap.threads.DEFAULT_RETRY_DELAYS
import com.sirelon.sellsnap.threads.withRetryIf
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A Ktor client for the Gemini API: Java engine, `expectSuccess = false`, so a failed response becomes
 * an [LlmFailure] here and never a Ktor exception. A drafting call thinks first, so the request
 * timeout is generous.
 */
fun geminiHttpClient(engine: HttpClientEngine = Java.create()): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10.seconds.inWholeMilliseconds
        requestTimeoutMillis = 120.seconds.inWholeMilliseconds
    }
}

/**
 * [LlmClient] over `models/{model}:generateContent`; field names follow
 * https://ai.google.dev/api/generate-content and https://ai.google.dev/gemini-api/docs/thinking (fetched 2026-10-10).
 *
 * The key travels in the `x-goog-api-key` header and never in the URL. No Ktor exception message
 * and no response text leaves this class: a failure carries a fixed message built from the HTTP
 * status or the API's block reason only.
 *
 * Structured output sets [STRUCTURED_OUTPUT_FIELD]; see [requestBody]. [retryDelays] are the pauses
 * before each retry of a [LlmFailure.Transient] failure.
 */
class GeminiLlmClient(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    private val retryDelays: List<Duration> = DEFAULT_RETRY_DELAYS,
) : LlmClient {
    override suspend fun complete(request: LlmRequest): LlmResult {
        require(MODEL_NAME.matches(request.model)) { "Model name has unexpected characters" }
        val body = requestBody(request).toString()
        return withRetryIf(retryDelays, { it is LlmFailure.Transient }) {
            parse(request.model, send(request.model, body))
        }
    }

    private suspend fun send(model: String, body: String): String {
        val response = try {
            client.post("$baseUrl/v1beta/models/$model:generateContent") {
                header(KEY_HEADER, apiKey)
                setBody(TextContent(body, ContentType.Application.Json))
            }.let { Response(it.status.value, it.status.isSuccess(), it.bodyAsText()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The exception message may hold request details, so it is dropped.
            throw LlmFailure.Transient("Gemini request failed (network error)")
        }
        if (response.success) return response.text
        throw failureFor(response.status)
    }

    private class Response(val status: Int, val success: Boolean, val text: String)

    private fun failureFor(status: Int): LlmFailure = when {
        status == 429 -> LlmFailure.RateLimited("Gemini rate limit reached (HTTP 429)")
        status >= 500 || status == 408 -> LlmFailure.Transient("Gemini temporary error (HTTP $status)")
        else -> LlmFailure.Permanent(status, "Gemini rejected the request (HTTP $status)")
    }

    private fun requestBody(request: LlmRequest): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { add(textPart(request.system)) }
        }
        putJsonArray("contents") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(textPart(request.user)) }
                },
            )
        }
        putJsonObject("generationConfig") {
            put("maxOutputTokens", request.maxOutputTokens)
            putJsonObject("thinkingConfig") { put("thinkingLevel", request.thinkingLevel) }
            if (request.responseSchema != null) {
                putJsonObject(STRUCTURED_OUTPUT_FIELD) {
                    putJsonObject("text") {
                        put("mimeType", JSON_MIME_TYPE)
                        put("schema", request.responseSchema)
                    }
                }
            }
        }
    }

    private fun textPart(text: String): JsonObject = buildJsonObject { put("text", text) }

    private fun parse(model: String, text: String): LlmResult {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw unreadable()

        val blockReason = root.obj("promptFeedback")?.string("blockReason")
        if (blockReason != null && blockReason != "BLOCK_REASON_UNSPECIFIED") throw refused("prompt", blockReason)

        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: throw unreadable()
        val finishReason = candidate.string("finishReason") ?: LlmResult.FINISH_STOP
        if (finishReason in BLOCKING_FINISH_REASONS) throw refused("answer", finishReason)

        val parts = candidate.obj("content")?.get("parts") as? JsonArray ?: JsonArray(emptyList())
        val answer = parts
            .mapNotNull { it as? JsonObject }
            .filter { (it["thought"] as? JsonPrimitive)?.booleanOrNull != true }
            .mapNotNull { it.string("text") }
            .joinToString("")

        val usage = root.obj("usageMetadata")
        return LlmResult(
            text = answer,
            finishReason = finishReason,
            usage = LlmUsage(
                model = model,
                promptTokens = usage?.long("promptTokenCount") ?: 0,
                outputTokens = usage?.long("candidatesTokenCount") ?: 0,
                thoughtsTokens = usage?.long("thoughtsTokenCount") ?: 0,
            ),
        )
    }

    private fun refused(what: String, reason: String): LlmFailure.Refused {
        val safeReason = reason.takeIf { REASON_NAME.matches(it) } ?: "UNKNOWN"
        return LlmFailure.Refused("Gemini blocked the $what ($safeReason)")
    }

    private fun unreadable() = LlmFailure.Transient("Gemini returned an unreadable response")

    private companion object {
        const val KEY_HEADER = "x-goog-api-key"
        const val STRUCTURED_OUTPUT_FIELD = "responseFormat"
        const val JSON_MIME_TYPE = "APPLICATION_JSON"
        val MODEL_NAME = Regex("[A-Za-z0-9._-]{1,80}")
        val REASON_NAME = Regex("[A-Z_]{1,40}")

        /** Finish reasons that the API's FinishReason enum documents for blocked output. */
        val BLOCKING_FINISH_REASONS = setOf("SAFETY", "RECITATION", "LANGUAGE", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII")
    }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
