package com.skydex.server.hypixel

import com.skydex.shared.model.ProfileSummary
import com.skydex.shared.model.SkyblockProfile
import com.skydex.shared.model.SlayerLevel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

// Maps the parts of Hypixel's `/v2/skyblock/profiles` JSON we use onto the shared DTOs. Every field is optional in
// practice (new profiles and disabled API settings drop whole sections), so missing values fall back to 0 or null.

internal fun summarize(profile: JsonObject): ProfileSummary = ProfileSummary(
    profileId = profile.string("profile_id").orEmpty(),
    cuteName = profile.string("cute_name").orEmpty(),
    gameMode = profile.string("game_mode"),
    selected = (profile["selected"] as? JsonPrimitive)?.booleanOrNull ?: false,
)

/** Null if [uuid] is not a member of [profile]. */
internal fun toSkyblockProfile(profile: JsonObject, uuid: String, username: String, fetchedAt: Long): SkyblockProfile? {
    val member = profile.obj("members")?.obj(uuid) ?: return null
    val skills = member.obj("player_data")?.obj("experience").orEmpty()
        .mapKeys { (key, _) -> key.removePrefix("SKILL_").lowercase() }
        .filterKeys { it in Leveling.SKILL_CAPS }
        .map { (name, xp) -> Leveling.skill(name, xp.double() ?: 0.0) }
    val slayers = member.obj("slayer")?.obj("slayer_bosses").orEmpty()
        .map { (boss, data) ->
            val xp = (data as? JsonObject)?.get("xp")?.double()?.toLong() ?: 0L
            SlayerLevel(boss, xp, Leveling.slayerLevel(boss, xp))
        }
    val catacombsXp = member.obj("dungeons")?.obj("dungeon_types")?.obj("catacombs")?.get("experience")?.double()

    return SkyblockProfile(
        profileId = profile.string("profile_id").orEmpty(),
        cuteName = profile.string("cute_name").orEmpty(),
        uuid = uuid,
        username = username,
        skyblockLevel = (member.obj("leveling")?.get("experience")?.double() ?: 0.0) / 100,
        purse = member.obj("currencies")?.get("coin_purse")?.double() ?: 0.0,
        bankBalance = profile.obj("banking")?.get("balance")?.double(),
        fairySouls = member.obj("fairy_soul")?.get("total_collected")?.double()?.toInt() ?: 0,
        skills = skills,
        slayers = slayers,
        catacombs = catacombsXp?.let(Leveling::catacombs),
        lastSave = null, // v2 no longer reports a member's last save.
        fetchedAt = fetchedAt,
    )
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement.double(): Double? = (this as? JsonPrimitive)?.doubleOrNull
