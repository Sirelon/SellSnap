package com.sirelon.sellsnap.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRoutes() {
    routing {
        post("/tick") { call.respond(HttpStatusCode.NotImplemented) }
        post("/batch") { call.respond(HttpStatusCode.NotImplemented) }
        post("/telegram") { call.respond(HttpStatusCode.NotImplemented) }
        get("/go/{code}") { call.respond(HttpStatusCode.NotImplemented) }
    }
}
