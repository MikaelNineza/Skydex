package com.skydex.server.hypixel

import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.SkyblockProfile

/** Where routes and jobs get player data. Implemented on top of the Mojang and Hypixel clients. */
interface ProfileSource {
    /** Resolves a username or undashed UUID. Throws [PlayerNotFoundException] if it doesn't exist. */
    suspend fun playerProfiles(nameOrUuid: String): PlayerProfiles

    /** Throws [PlayerNotFoundException] if the player isn't a member of that profile. */
    suspend fun profile(uuid: String, profileId: String): SkyblockProfile
}

class PlayerNotFoundException(message: String) : Exception(message)

/** Hypixel or Mojang failed or rate-limited us; routes map this to 502/503. */
class UpstreamException(message: String, cause: Throwable? = null) : Exception(message, cause)
