package com.sirelon.sellsnap.threads

import com.sirelon.sellsnap.model.PostKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.time.Duration.Companion.seconds

/**
 * A Ktor client for the Threads API: Java engine, `expectSuccess = false`, so a failed response
 * becomes a [ThreadsFailure] here and never a Ktor exception whose message carries the URL.
 */
fun threadsHttpClient(engine: HttpClientEngine = Java.create()): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 10.seconds.inWholeMilliseconds
        requestTimeoutMillis = 30.seconds.inWholeMilliseconds
    }
}

/**
 * [ThreadsApi] over `graph.threads.net`. Writes are POST form bodies. The token endpoints and the
 * reads are documented as GET and stay GET, so tokens and the app secret travel in the query
 * string; that is safe because no URL is ever logged or put in a failure message.
 *
 * Every failure carries a fixed message built from the HTTP status and the Graph error code only;
 * no Ktor exception message and no response text leaves this class.
 *
 * Inferred: the error body is the standard Graph shape `{"error":{"code":..,"is_transient":..}}`;
 * the fetched Threads pages do not show it.
 */
class ThreadsHttpClient(
    private val client: HttpClient,
    private val appSecret: String,
    private val baseUrl: String = "https://graph.threads.net",
) : ThreadsApi {
    override suspend fun debugToken(inputToken: String): TokenInfo {
        // The call wants a tester's user token as `access_token`; the token under test is one.
        val body = get(
            listOf("debug_token"),
            "access_token" to inputToken,
            "input_token" to inputToken,
        )
        val data = body["data"] as? JsonObject ?: throw unreadable()
        return TokenInfo(
            isValid = data.bool("is_valid") ?: false,
            scopes = (data["scopes"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
            userId = data.text("user_id"),
            expiresAt = data.epoch("expires_at"),
            dataAccessExpiresAt = data.epoch("data_access_expires_at"),
            issuedAt = data.epoch("issued_at"),
        )
    }

    override suspend fun exchangeToken(shortLivedToken: String): IssuedToken = issued(
        get(
            listOf("access_token"),
            "grant_type" to "th_exchange_token",
            "client_secret" to appSecret,
            "access_token" to shortLivedToken,
            versioned = false,
        ),
    )

    override suspend fun refreshToken(longLivedToken: String): IssuedToken = issued(
        get(
            listOf("refresh_access_token"),
            "grant_type" to "th_refresh_token",
            "access_token" to longLivedToken,
            versioned = false,
        ),
    )

    override suspend fun me(token: String): Me {
        val body = get(listOf("me"), "fields" to "id,username", "access_token" to token)
        return Me(id = body.requireText("id"), username = body.requireText("username"))
    }

    override suspend fun createContainer(userId: String, token: String, request: ContainerRequest): String {
        val fields = buildList {
            when (request.kind) {
                PostKind.Image -> {
                    add("media_type" to "IMAGE")
                    add("image_url" to request.imageUrl.orEmpty())
                }
                PostKind.Text, PostKind.Poll -> add("media_type" to "TEXT")
            }
            if (request.text.isNotEmpty()) add("text" to request.text)
            if (request.kind == PostKind.Poll) add("poll_attachment" to pollAttachment(request.pollOptions))
            if (request.replyToId != null) add("reply_to_id" to request.replyToId)
            add("access_token" to token)
        }
        return post(listOf(userId, "threads"), fields).requireText("id")
    }

    override suspend fun containerStatus(containerId: String, token: String): ContainerStatus {
        val body = get(listOf(containerId), "fields" to "status,error_message", "access_token" to token)
        val state = when (body.text("status")) {
            "IN_PROGRESS" -> ContainerState.InProgress
            "FINISHED" -> ContainerState.Finished
            "ERROR" -> ContainerState.Error
            "EXPIRED" -> ContainerState.Expired
            "PUBLISHED" -> ContainerState.Published
            else -> throw unreadable()
        }
        // The documented messages are codes such as UNKNOWN; free text is dropped.
        val message = body.text("error_message")?.takeIf { ERROR_CODE.matches(it) }
        return ContainerStatus(state, message)
    }

    override suspend fun publish(userId: String, token: String, containerId: String): String =
        post(
            listOf(userId, "threads_publish"),
            listOf("creation_id" to containerId, "access_token" to token),
        ).requireText("id")

    override suspend fun publishingLimit(userId: String, token: String): PublishingLimit {
        val body = get(
            listOf(userId, "threads_publishing_limit"),
            "fields" to "quota_usage,config",
            "access_token" to token,
        )
        val entry = (body["data"] as? JsonArray)?.firstOrNull() as? JsonObject ?: throw unreadable()
        val total = (entry["config"] as? JsonObject)?.int("quota_total") ?: entry.int("quota_total")
            ?: throw unreadable()
        return PublishingLimit(quotaUsage = entry.int("quota_usage") ?: 0, quotaTotal = total)
    }

    override suspend fun delete(mediaId: String, token: String) {
        // The documented success body has a trailing comma and is not valid JSON, so a 2xx is the result.
        send {
            client.delete {
                target(listOf(mediaId), versioned = true)
                parameter("access_token", token)
            }
        }
    }

    override suspend fun recentThreads(userId: String, token: String, limit: Int): List<ThreadSummary> {
        val body = get(
            listOf(userId, "threads"),
            "fields" to "id,text,timestamp",
            "limit" to limit.toString(),
            "access_token" to token,
        )
        val items = body["data"] as? JsonArray ?: throw unreadable()
        return items.map { item ->
            val entry = item as? JsonObject ?: throw unreadable()
            ThreadSummary(
                id = entry.requireText("id"),
                text = entry.text("text"),
                publishedAt = entry.text("timestamp")?.let(::parseTimestamp),
            )
        }
    }

    override suspend fun mediaInsights(mediaId: String, token: String, metrics: List<String>): Map<String, Long> {
        val body = get(
            listOf(mediaId, "insights"),
            "metric" to metrics.joinToString(","),
            "access_token" to token,
        )
        val items = body["data"] as? JsonArray ?: throw unreadable()
        return items.mapNotNull { item ->
            val entry = item as? JsonObject ?: throw unreadable()
            val name = entry.text("name") ?: throw unreadable()
            entry.insightValue()?.let { name to it }
        }.toMap()
    }

    override suspend fun followersCount(userId: String, token: String): Long {
        val body = get(
            listOf(userId, "threads_insights"),
            "metric" to "followers_count",
            "access_token" to token,
        )
        val items = body["data"] as? JsonArray ?: throw unreadable()
        val entry = items.firstNotNullOfOrNull { (it as? JsonObject)?.takeIf { e -> e.text("name") == "followers_count" } }
        return entry?.insightValue() ?: throw unreadable()
    }

    private suspend fun get(
        segments: List<String>,
        vararg params: Pair<String, String>,
        versioned: Boolean = true,
    ): JsonObject = parseObject(
        send {
            client.get {
                target(segments, versioned)
                params.forEach { (name, value) -> parameter(name, value) }
            }
        },
    )

    private suspend fun post(segments: List<String>, fields: List<Pair<String, String>>): JsonObject = parseObject(
        send {
            client.submitForm(
                formParameters = parameters { fields.forEach { (name, value) -> append(name, value) } },
            ) { target(segments, versioned = true) }
        },
    )

    private fun HttpRequestBuilder.target(segments: List<String>, versioned: Boolean) {
        url {
            takeFrom(baseUrl)
            appendPathSegments(listOfNotNull(VERSION.takeIf { versioned }) + segments)
        }
    }

    /** Returns the body of a 2xx response; every other outcome becomes a [ThreadsFailure]. */
    private suspend fun send(request: suspend () -> HttpResponse): String {
        val response = try {
            request().let { Response(it.status.value, it.status.isSuccess(), it.bodyAsText()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The exception message may hold the request URL with its token, so it is dropped.
            throw ThreadsFailure.Transient("Threads request failed (network error)")
        }
        if (response.success) return response.text
        throw failureFor(response.status, response.text)
    }

    private class Response(val status: Int, val success: Boolean, val text: String)

    private fun failureFor(status: Int, body: String): ThreadsFailure {
        val error = runCatching { Json.parseToJsonElement(body).jsonObject["error"] as? JsonObject }.getOrNull()
        return classifyResponse(
            httpStatus = status,
            code = error?.int("code"),
            isTransient = error?.bool("is_transient") ?: false,
        )
    }

    private fun parseObject(text: String): JsonObject =
        try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw unreadable()
        }

    private fun pollAttachment(options: List<String>): String = buildJsonObject {
        options.take(POLL_KEYS.size).forEachIndexed { index, option -> put(POLL_KEYS[index], option) }
    }.toString()

    private fun issued(body: JsonObject): IssuedToken =
        IssuedToken(body.requireText("access_token"), body.long("expires_in") ?: throw unreadable())

    private companion object {
        const val VERSION = "v1.0"
        val POLL_KEYS = listOf("option_a", "option_b", "option_c", "option_d")
        val ERROR_CODE = Regex("[A-Z_]{1,40}")
    }
}

private fun unreadable() = ThreadsFailure.Transient("Threads returned an unreadable response")

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonObject.requireText(key: String): String = text(key) ?: throw unreadable()

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

/** A metric entry's number: `values[0].value` for lifetime metrics, `total_value.value` for user totals. */
private fun JsonObject.insightValue(): Long? {
    val holder = (this["total_value"] as? JsonObject) ?: (this["values"] as? JsonArray)?.firstOrNull() as? JsonObject
    return holder?.long("value")
}

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

/** Epoch seconds as an instant; 0 means "none" in the debug response. */
private fun JsonObject.epoch(key: String): Instant? = long(key)?.takeIf { it > 0 }?.let(Instant::ofEpochSecond)

private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

private fun parseTimestamp(text: String): Instant? =
    try {
        OffsetDateTime.parse(text, TIMESTAMP).toInstant()
    } catch (e: DateTimeParseException) {
        null
    }
