package com.skydex.server

import com.skydex.server.hypixel.ProfileSource
import com.skydex.server.hypixel.UpstreamException
import com.skydex.server.plugins.configureMonitoring
import com.skydex.server.plugins.configureSerialization
import com.skydex.server.routes.configureRouting
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.SkyblockProfile
import io.ktor.server.application.Application

// Loaded by EngineMain through ktor.application.modules in application.conf.
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureRouting()
    // TODO(merge): pass the real HypixelProfileSource once the Hypixel client is on this branch.
    configureData(profileSource = UnwiredProfileSource)
}

// TODO(merge): delete once HypixelProfileSource is wired into module(). Every call fails, so snapshots are skipped.
private object UnwiredProfileSource : ProfileSource {
    override suspend fun playerProfiles(nameOrUuid: String): PlayerProfiles =
        throw UpstreamException("ProfileSource is not wired yet")

    override suspend fun profile(uuid: String, profileId: String): SkyblockProfile =
        throw UpstreamException("ProfileSource is not wired yet")
}
