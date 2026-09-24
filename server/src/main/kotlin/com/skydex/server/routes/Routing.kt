package com.skydex.server.routes

import com.skydex.shared.model.ServerHealth
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respond(ServerHealth(status = "ok", version = "0.1.0"))
        }
    }
}
