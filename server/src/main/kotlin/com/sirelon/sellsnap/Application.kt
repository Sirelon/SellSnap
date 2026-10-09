package com.sirelon.sellsnap

import com.sirelon.sellsnap.http.configureRoutes
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val config = Config.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureRoutes()
}
