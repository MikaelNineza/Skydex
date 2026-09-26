package com.skydex.shared.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** `GET /v1/players/{name}`: a Minecraft player and their Skyblock profiles. */
@Serializable
data class PlayerProfiles(
    /** Undashed Minecraft UUID. */
    val uuid: String,
    val username: String,
    val profiles: List<ProfileSummary>,
)

@Serializable
data class ProfileSummary(
    val profileId: String,
    /** Fruit name shown in game, e.g. "Mango". */
    val cuteName: String,
    /** null for normal profiles, otherwise e.g. "ironman", "island", "bingo". */
    val gameMode: String? = null,
    /** True for the profile the player last played on. */
    val selected: Boolean,
)

/** `GET /v1/players/{uuid}/profiles/{profileId}`: one member's view of a profile. */
@Serializable
data class SkyblockProfile(
    val profileId: String,
    val cuteName: String,
    val uuid: String,
    val username: String,
    /** Skyblock level with fractional progress, e.g. 212.45. */
    val skyblockLevel: Double,
    val purse: Double,
    /** null when the player has the Banking API disabled. */
    val bankBalance: Double? = null,
    val fairySouls: Int,
    /**
     * Fairy souls in the game. The server fills it from its own constant, so a new total only needs a server
     * deploy; the default covers cached profiles and servers that predate the field.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val fairySoulsTotal: Int = 289,
    /** Mean level of the non-cosmetic skills with progress, e.g. 52.47. null from servers that predate it. */
    val skillAverage: Double? = null,
    val skills: List<SkillLevel>,
    val slayers: List<SlayerLevel>,
    val catacombs: SkillLevel? = null,
    /** Unix millis of the member's last save, if known. */
    val lastSave: Long? = null,
    /** Unix millis when the server fetched this from Hypixel. */
    val fetchedAt: Long,
)

@Serializable
data class SkillLevel(
    /** Lowercase Hypixel name without the SKILL_ prefix, e.g. "farming", "catacombs". */
    val name: String,
    val experience: Double,
    val level: Int,
    val maxLevel: Int,
    /** Progress towards the next level in 0.0..1.0 (1.0 when maxed). */
    val progress: Double,
)

@Serializable
data class SlayerLevel(
    /** Lowercase Hypixel boss id, e.g. "zombie", "spider", "wolf", "enderman", "blaze", "vampire". */
    val boss: String,
    val experience: Long,
    val level: Int,
)
