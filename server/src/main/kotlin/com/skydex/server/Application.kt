package com.skydex.server

import com.skydex.server.hypixel.hypixelProfileSource
import com.skydex.server.plugins.configureMonitoring
import com.skydex.server.plugins.configureSerialization
import com.skydex.server.plugins.configureStatusPages
import com.skydex.server.routes.configurePlayerRoutes
import com.skydex.server.routes.configureRouting
import io.ktor.server.application.Application

// Loaded by EngineMain through ktor.application.modules in application.conf.
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureStatusPages()
    configureRouting()
    val profileSource = hypixelProfileSource()
    configurePlayerRoutes(profileSource)
}
