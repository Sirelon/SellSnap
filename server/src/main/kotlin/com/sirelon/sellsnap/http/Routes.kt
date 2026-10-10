package com.sirelon.sellsnap.http

import com.sirelon.sellsnap.AppGraph
import com.sirelon.sellsnap.batch.BatchResult
import com.sirelon.sellsnap.redirect.ClickEvent
import com.sirelon.sellsnap.telegram.Update
import com.sirelon.sellsnap.threads.redact
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import io.ktor.utils.io.readRemaining
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val updateJson = Json { ignoreUnknownKeys = true }

fun Application.configureRoutes(graph: AppGraph) {
    routing {
        post("/tick") {
            call.guarded {
                if (!call.isScheduler(graph)) return@guarded
                val summary = graph.ticks.run()
                call.respondText(
                    summary.line(),
                    status = if (summary.timedOut) HttpStatusCode.InternalServerError else HttpStatusCode.OK,
                )
            }
        }
        post("/batch") {
            call.guarded {
                if (!call.isScheduler(graph)) return@guarded
                handleBatch(call, graph)
            }
        }
        post("/telegram") {
            call.guarded {
                // The secret is checked before the body is touched.
                if (!graph.telegramSecret.matches(call.request.headers[TelegramSecret.HEADER])) {
                    return@guarded call.respond(HttpStatusCode.Forbidden)
                }
                handleTelegram(call, graph)
            }
        }
        post("/click") {
            handleClick(call, graph)
        }
        get("/go/{code}") {
            call.guarded {
                val code = call.parameters["code"].orEmpty()
                val target = graph.redirects.resolve(code, call.request.headers[HttpHeaders.UserAgent])
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.respondRedirect(target)
            }
        }
    }
}

/**
 * `POST /batch`: runs the weekly drafting batch. The optional JSON body `{"accounts": ["ua"]}` limits the run
 * to those accounts. 200 when the batch is done or was done already; 409 while another run holds the lease;
 * 503 when the run timed out or stopped on a busy model, so Cloud Scheduler retries; 500 when an account failed.
 */
private suspend fun handleBatch(call: ApplicationCall, graph: AppGraph) {
    val body = call.receiveText()
    val accounts = if (body.isBlank()) {
        null
    } else {
        parseBatchAccounts(body) ?: return call.respondText("The body must be {\"accounts\": [\"id\", ...]} with at least one id.", status = HttpStatusCode.BadRequest)
    }
    val result = graph.batch.run(accounts)
    val status = when (result) {
        is BatchResult.Done, is BatchResult.AlreadyDone -> HttpStatusCode.OK
        is BatchResult.Running -> HttpStatusCode.Conflict
        is BatchResult.Deferred, is BatchResult.TimedOut -> HttpStatusCode.ServiceUnavailable
        is BatchResult.Failed -> HttpStatusCode.InternalServerError
    }
    call.respondText(result.line(), status = status)
}

/** The account ids of a batch body, or null when the body is not `{"accounts": [non-empty list of strings]}`. */
private fun parseBatchAccounts(text: String): List<String>? {
    val obj = try {
        updateJson.parseToJsonElement(text) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: return null
    val ids = (obj["accounts"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }
    return ids?.takeIf { it.isNotEmpty() }
}

/**
 * `POST /click`: the website reports a visit or a store tap for a post code. The body is text holding
 * `{"p": "<code>", "e": "view" | "play" | "appstore"}`, because `navigator.sendBeacon` sends
 * `text/plain` and needs no CORS preflight. The answer is always 204 without cookies, so a caller
 * learns nothing about which codes exist. Only a malformed body is logged, as one line without the body.
 */
private suspend fun handleClick(call: ApplicationCall, graph: AppGraph) {
    call.response.header(HttpHeaders.CacheControl, "no-store")
    call.response.header(HttpHeaders.AccessControlAllowOrigin, WEBSITE_ORIGIN)
    val body = try {
        call.receiveChannel().readRemaining(MAX_CLICK_BODY + 1L).readString()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    val parsed = body?.takeIf { it.length <= MAX_CLICK_BODY }?.let(::parseClickBody)
    if (parsed == null) {
        call.application.log.warn("Click body was malformed")
    } else {
        val (code, event) = parsed
        if (event != null) graph.redirects.report(code, event, call.request.headers[HttpHeaders.UserAgent])
    }
    call.respond(HttpStatusCode.NoContent)
}

/** The code and event of a click body, with a null event for an event name we do not know; null when the body is not the expected shape. */
private fun parseClickBody(text: String): Pair<String, ClickEvent?>? {
    val obj = try {
        updateJson.parseToJsonElement(text) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: return null
    val code = (obj["p"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    val event = (obj["e"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    return code to ClickEvent.fromWire(event)
}

/** Answers 401 and returns false unless the request carries a valid scheduler OIDC token. */
private suspend fun ApplicationCall.isScheduler(graph: AppGraph): Boolean {
    val token = bearerToken(request.headers[HttpHeaders.Authorization])
    if (token != null && graph.oidc.verify(token)) return true
    respond(HttpStatusCode.Unauthorized)
    return false
}

/** Telegram gets 200 for every update once the secret is right, so it never redelivers one. */
private suspend fun handleTelegram(call: ApplicationCall, graph: AppGraph) {
    val update = try {
        updateJson.decodeFromString<Update>(call.receiveText())
    } catch (e: SerializationException) {
        // The body is not logged: it may hold a token typed by the owner.
        call.application.log.warn("Telegram update was not valid JSON for an Update")
        return call.respond(HttpStatusCode.OK)
    } catch (e: IllegalArgumentException) {
        call.application.log.warn("Telegram update was not valid JSON for an Update")
        return call.respond(HttpStatusCode.OK)
    }
    try {
        graph.updates.handle(update)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        call.application.log.error(redactedLine("Telegram update failed", e))
    }
    call.respond(HttpStatusCode.OK)
}

/**
 * Runs [block]; an exception becomes a 500 and one redacted log line, never a stack trace, because
 * a client exception message can carry a request URL with a token in it.
 */
private suspend fun ApplicationCall.guarded(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        application.log.error(redactedLine("${request.httpMethod.value} ${request.path().take(MAX_PATH_LOG)} failed", e))
        if (!response.isCommitted) respond(HttpStatusCode.InternalServerError)
    }
}

private fun redactedLine(what: String, e: Exception): String =
    redact("$what: ${e::class.simpleName}: ${e.message.orEmpty()}").replace(Regex("\\s+"), " ").take(MAX_LOG_LINE)

/** The GitHub Pages site that reports clicks; it is the only origin a browser `fetch` may read the answer for. */
private const val WEBSITE_ORIGIN = "https://sirelon.github.io"

/** A real click body is under 100 characters. */
private const val MAX_CLICK_BODY = 512

private const val MAX_PATH_LOG = 40
private const val MAX_LOG_LINE = 300
