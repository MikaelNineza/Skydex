package com.skydex.server.hypixel

import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.SkyblockProfile
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * [ProfileSource] backed by Mojang and Hypixel, with short in-memory caches so repeated requests for the same player
 * don't spend the API key's rate limit: Hypixel profiles for [profileTtlMillis], name lookups for [nameTtlMillis].
 */
class HypixelProfileSource(
    private val mojang: MojangClient,
    private val hypixel: HypixelClient,
    private val clock: () -> Long = System::currentTimeMillis,
    profileTtlMillis: Long = 60_000,
    nameTtlMillis: Long = 10 * 60_000,
) : ProfileSource {
    private class Fetched(val profiles: List<JsonObject>, val fetchedAt: Long)

    private val names = TtlCache<String, MojangProfile>(nameTtlMillis, clock)
    private val profiles = TtlCache<String, Fetched>(profileTtlMillis, clock)

    override suspend fun playerProfiles(nameOrUuid: String): PlayerProfiles {
        val player = resolve(nameOrUuid)
        val summaries = fetch(player.id).profiles.map(::summarize)
        return PlayerProfiles(uuid = player.id, username = player.name, profiles = summaries)
    }

    override suspend fun profile(uuid: String, profileId: String): SkyblockProfile {
        val id = undashedUuidOrNull(uuid) ?: throw PlayerNotFoundException("Invalid UUID $uuid")
        val fetched = fetch(id)
        // Compare without dashes or case so either UUID form of the profile id works.
        val wanted = profileId.replace("-", "").lowercase()
        val profile = fetched.profiles.firstOrNull { summarize(it).profileId.replace("-", "").lowercase() == wanted }
            ?: throw PlayerNotFoundException("Player $id has no profile $profileId")
        val username = resolve(id).name
        return toSkyblockProfile(profile, id, username, fetched.fetchedAt)
            ?: throw PlayerNotFoundException("Player $id is not a member of profile $profileId")
    }

    private suspend fun resolve(nameOrUuid: String): MojangProfile {
        val uuid = undashedUuidOrNull(nameOrUuid)
        return names.getOrPut(uuid ?: nameOrUuid.lowercase()) {
            if (uuid != null) mojang.byUuid(uuid) else mojang.byUsername(nameOrUuid)
        }
    }

    private suspend fun fetch(uuid: String): Fetched =
        profiles.getOrPut(uuid) { Fetched(hypixel.skyblockProfiles(uuid), clock()) }
}

/** Minimal time-based cache. Doesn't deduplicate concurrent misses; good enough for a handful of users. */
internal class TtlCache<K : Any, V : Any>(private val ttlMillis: Long, private val clock: () -> Long) {
    private val entries = ConcurrentHashMap<K, Pair<Long, V>>()

    suspend fun getOrPut(key: K, load: suspend () -> V): V {
        val now = clock()
        entries[key]?.let { (storedAt, value) -> if (now - storedAt < ttlMillis) return value }
        if (entries.size > MAX_ENTRIES) entries.values.removeIf { now - it.first >= ttlMillis }
        return load().also { entries[key] = now to it }
    }

    private companion object {
        const val MAX_ENTRIES = 1_000
    }
}
