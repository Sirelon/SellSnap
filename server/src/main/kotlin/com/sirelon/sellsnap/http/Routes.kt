package com.sirelon.sellsnap.http

import com.sirelon.sellsnap.AppGraph
import com.sirelon.sellsnap.telegram.Update
import com.sirelon.sellsnap.threads.redact
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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
                call.respond(HttpStatusCode.NotImplemented)
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

private const val MAX_PATH_LOG = 40
private const val MAX_LOG_LINE = 300
