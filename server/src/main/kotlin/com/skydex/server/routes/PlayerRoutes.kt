package com.skydex.server.routes

import com.skydex.server.hypixel.ProfileSource
import com.skydex.server.hypixel.isValidUsername
import com.skydex.server.hypixel.undashedUuidOrNull
import io.ktor.server.application.Application
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * `GET /v1/players/{name}` -> [com.skydex.shared.model.PlayerProfiles], where name is a username or UUID.
 * `GET /v1/players/{uuid}/profiles/{profileId}` -> [com.skydex.shared.model.SkyblockProfile].
 */
fun Application.configurePlayerRoutes(source: ProfileSource) {
    routing {
        get("/v1/players/{name}") {
            val name = call.parameters["name"].orEmpty()
            val key = undashedUuidOrNull(name)
                ?: name.takeIf(::isValidUsername)
                ?: throw BadRequestException("Not a Minecraft username or UUID: $name")
            call.respond(source.playerProfiles(key))
        }
        get("/v1/players/{uuid}/profiles/{profileId}") {
            val uuid = uuidParameter(call.parameters["uuid"], "player UUID")
            val profileId = uuidParameter(call.parameters["profileId"], "profile id")
            call.respond(source.profile(uuid, profileId))
        }
    }
}

private fun uuidParameter(value: String?, what: String): String =
    value?.let(::undashedUuidOrNull) ?: throw BadRequestException("Invalid $what: $value")
