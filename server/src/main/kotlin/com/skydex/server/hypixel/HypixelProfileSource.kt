package com.skydex.server.hypixel

import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.PlayerRank
import com.skydex.shared.model.SkyblockProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * [ProfileSource] backed by Mojang and Hypixel, with short in-memory caches so repeated requests for the same player
 * don't spend the API key's rate limit: Hypixel profiles for [profileTtlMillis], name lookups for [nameTtlMillis],
 * ranks for [rankTtlMillis]. Profiles get the player's rank and skin face when those can be loaded; without [skins]
 * they have no face.
 */
class HypixelProfileSource(
    private val mojang: MojangClient,
    private val hypixel: HypixelClient,
    private val skins: SkinClient? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    profileTtlMillis: Long = 60_000,
    nameTtlMillis: Long = 10 * 60_000,
    rankTtlMillis: Long = 60 * 60_000,
) : ProfileSource {
    private class Fetched(val profiles: List<JsonObject>, val fetchedAt: Long)

    /** Wraps the rank so "no rank" (null) can be cached too; [failed] lookups are cached only briefly. */
    private class CachedRank(val rank: PlayerRank?, val failed: Boolean = false)

    private val log = LoggerFactory.getLogger(HypixelProfileSource::class.java)
    private val names = TtlCache<String, MojangProfile>(nameTtlMillis, clock)
    private val profiles = TtlCache<String, Fetched>(profileTtlMillis, clock)
    private val ranks = TtlCache<String, CachedRank>(rankTtlMillis, clock) {
        if (it.failed) FAILURE_TTL_MILLIS else rankTtlMillis
    }

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
        val player = resolve(id)
        val member = toSkyblockProfile(profile, id, player.name, fetched.fetchedAt)
            ?: throw PlayerNotFoundException("Player $id is not a member of profile $profileId")
        // Only after the profile loaded, so a rank lookup never spends the key's quota on a failed request. Rank and
        // face are extras: they load in parallel, and a slow or failed lookup leaves them null instead of failing.
        return coroutineScope {
            val rank = async { withTimeoutOrNull(EXTRAS_TIMEOUT_MILLIS) { rank(id) } }
            val face = async { withTimeoutOrNull(EXTRAS_TIMEOUT_MILLIS) { player.skinUrl()?.let { skins?.face(it) } } }
            member.copy(rank = rank.await(), face = face.await())
        }
    }

    /**
     * The player's rank, or null if they have none or it can't be loaded right now. A failure is cached for a few
     * minutes, and while Hypixel is rate limiting us the lookup is skipped so it can't spend quota or extend the block.
     */
    private suspend fun rank(uuid: String): PlayerRank? = ranks.getOrPut(uuid) {
        try {
            if (hypixel.isRateLimited) CachedRank(null, failed = true) else CachedRank(parseRank(hypixel.player(uuid)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not load the rank of {}: {}", uuid, e.toString())
            CachedRank(null, failed = true)
        }
    }.rank

    private suspend fun resolve(nameOrUuid: String): MojangProfile {
        val uuid = undashedUuidOrNull(nameOrUuid)
        return names.getOrPut(uuid ?: nameOrUuid.lowercase()) {
            if (uuid != null) mojang.byUuid(uuid) else mojang.byUsername(nameOrUuid)
        }
    }

    private suspend fun fetch(uuid: String): Fetched =
        profiles.getOrPut(uuid) { Fetched(hypixel.skyblockProfiles(uuid), clock()) }

    private companion object {
        const val EXTRAS_TIMEOUT_MILLIS = 3_000L
        const val FAILURE_TTL_MILLIS = 5 * 60_000L
    }
}

/** Minimal time-based cache. Doesn't deduplicate concurrent misses; good enough for a handful of users. */
internal class TtlCache<K : Any, V : Any>(
    ttlMillis: Long,
    private val clock: () -> Long,
    /** How long a given value stays fresh, for caching failures more briefly than successes. */
    private val ttlOf: (V) -> Long = { ttlMillis },
) {
    private val entries = ConcurrentHashMap<K, Pair<Long, V>>()

    suspend fun getOrPut(key: K, load: suspend () -> V): V {
        val now = clock()
        entries[key]?.let { (storedAt, value) -> if (now - storedAt < ttlOf(value)) return value }
        if (entries.size > MAX_ENTRIES) entries.values.removeIf { (storedAt, value) -> now - storedAt >= ttlOf(value) }
        return load().also { entries[key] = now to it }
    }

    private companion object {
        const val MAX_ENTRIES = 1_000
    }
}
