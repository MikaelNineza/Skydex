package com.skydex.server.hypixel

import com.skydex.shared.model.SkillLevel

/**
 * Skyblock XP tables and level math.
 *
 * Skill tables match Hypixel's `GET /v2/resources/skyblock/skills` (fetched 2026-09). Each table lists the XP needed
 * to go from one level to the next, so `table[0]` is the XP for level 1.
 *
 * Simplification: the API only reports XP, not unlocked caps. Farming (Anita perks) and taming (pet donations)
 * start at 50 and can be raised to 60; we always use the full cap of 60, so a player who hasn't unlocked the extra
 * levels may show a level above what the game shows. Catacombs overflow levels past 50 are ignored.
 */
object Leveling {
    /** Standard skill table, levels 1..60. Shorter-capped skills use a prefix of it. */
    val SKILL_XP: List<Long> = listOf(
        50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
        5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
        300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000,
        1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000, 2_200_000,
        2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
        4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000,
    )

    /** Runecrafting, levels 1..25. */
    val RUNECRAFTING_XP: List<Long> = listOf(
        50, 100, 125, 160, 200, 250, 315, 400, 500, 625,
        785, 1_000, 1_250, 1_565, 2_000, 2_500, 3_125, 4_000, 5_000, 6_250,
        7_850, 9_800, 12_250, 15_300, 19_050,
    )

    /** Social, levels 1..25. */
    val SOCIAL_XP: List<Long> = listOf(
        50, 100, 150, 250, 500, 750, 1_000, 1_250, 1_500, 2_000,
        2_500, 3_000, 3_750, 4_500, 6_000, 8_000, 10_000, 12_500, 15_000, 20_000,
        25_000, 30_000, 35_000, 40_000, 50_000,
    )

    /** Catacombs, levels 1..50. */
    val CATACOMBS_XP: List<Long> = listOf(
        50, 75, 110, 160, 230, 330, 470, 670, 950, 1_340,
        1_890, 2_665, 3_760, 5_260, 7_380, 10_300, 14_400, 20_000, 27_600, 38_000,
        52_500, 71_500, 97_000, 132_000, 180_000, 243_000, 328_000, 445_000, 600_000, 800_000,
        1_065_000, 1_410_000, 1_900_000, 2_500_000, 3_300_000, 4_300_000, 5_600_000, 7_200_000, 9_200_000, 12_000_000,
        15_000_000, 19_000_000, 24_000_000, 30_000_000, 38_000_000, 48_000_000, 60_000_000, 75_000_000, 93_000_000,
        116_250_000,
    )

    /**
     * Max level per skill (lowercase name without `SKILL_`). Also the list of real skills: Hypixel stores
     * bookkeeping values such as `SKILL_FORAGING_EXTRA_LEVEL_CAP` under the same prefix.
     */
    val SKILL_CAPS = mapOf(
        "farming" to 60, "mining" to 60, "combat" to 60, "enchanting" to 60, "taming" to 60,
        "foraging" to 57, "fishing" to 50, "alchemy" to 50, "carpentry" to 50, "hunting" to 50,
        "runecrafting" to 25, "social" to 25,
    )

    /** Cumulative XP needed for each slayer level, starting at level 1. */
    val SLAYER_XP: Map<String, List<Long>> = mapOf(
        "zombie" to listOf(5, 15, 200, 1_000, 5_000, 20_000, 100_000, 400_000, 1_000_000),
        "spider" to listOf(5, 25, 200, 1_000, 5_000, 20_000, 100_000, 400_000, 1_000_000),
        "wolf" to listOf(10, 30, 250, 1_500, 5_000, 20_000, 100_000, 400_000, 1_000_000),
        "enderman" to listOf(10, 30, 250, 1_500, 5_000, 20_000, 100_000, 400_000, 1_000_000),
        "blaze" to listOf(10, 30, 250, 1_500, 5_000, 20_000, 100_000, 400_000, 1_000_000),
        "vampire" to listOf(20, 75, 240, 840, 2_400),
    )

    /** Level for a skill given its lowercase name, e.g. "farming" or "runecrafting". */
    fun skill(name: String, experience: Double): SkillLevel {
        val table = when (name) {
            "runecrafting" -> RUNECRAFTING_XP
            "social" -> SOCIAL_XP
            else -> SKILL_XP
        }
        return level(name, experience, table.take(SKILL_CAPS[name] ?: 50))
    }

    fun catacombs(experience: Double): SkillLevel = level("catacombs", experience, CATACOMBS_XP)

    /** Slayer level for [boss]; 0 for bosses we don't have a table for. */
    fun slayerLevel(boss: String, experience: Long): Int =
        SLAYER_XP[boss]?.count { experience >= it } ?: 0

    /** Walks a per-level table: level is the number of levels fully paid for, capped at the table size. */
    fun level(name: String, experience: Double, table: List<Long>): SkillLevel {
        var remaining = experience
        var level = 0
        while (level < table.size && remaining >= table[level]) {
            remaining -= table[level]
            level++
        }
        val progress = if (level == table.size) 1.0 else remaining / table[level]
        return SkillLevel(name, experience, level, table.size, progress)
    }
}
