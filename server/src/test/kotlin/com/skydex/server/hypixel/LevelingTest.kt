package com.skydex.server.hypixel

import kotlin.test.Test
import kotlin.test.assertEquals

class LevelingTest {
    @Test
    fun tableTotalsMatchKnownMaxXp() {
        assertEquals(55_172_425, Leveling.SKILL_XP.take(50).sum())
        assertEquals(111_672_425, Leveling.SKILL_XP.sum())
        assertEquals(94_450, Leveling.RUNECRAFTING_XP.sum())
        assertEquals(272_800, Leveling.SOCIAL_XP.sum())
        assertEquals(569_809_640, Leveling.CATACOMBS_XP.sum())
    }

    @Test
    fun skillLevelAndProgress() {
        val zero = Leveling.skill("combat", 0.0)
        assertEquals(0, zero.level)
        assertEquals(0.0, zero.progress)

        // 50 XP for level 1, then 75 of the 125 needed for level 2.
        val combat = Leveling.skill("combat", 125.0)
        assertEquals(1, combat.level)
        assertEquals(0.6, combat.progress, 1e-9)
        assertEquals(60, combat.maxLevel)

        val fishing = Leveling.skill("fishing", 55_172_425.0)
        assertEquals(50, fishing.level)
        assertEquals(50, fishing.maxLevel)
        assertEquals(1.0, fishing.progress)

        val farming = Leveling.skill("farming", 55_172_425.0)
        assertEquals(50, farming.level)
        assertEquals(0.0, farming.progress)
    }

    @Test
    fun capsAreRespected() {
        assertEquals(60, Leveling.skill("mining", 1e12).level)
        assertEquals(57, Leveling.skill("foraging", 1e12).level)
        assertEquals(25, Leveling.skill("runecrafting", 94_450.0).level)
        assertEquals(24, Leveling.skill("runecrafting", 94_449.0).level)
        assertEquals(25, Leveling.skill("social", 1e9).maxLevel)
    }

    @Test
    fun catacombs() {
        assertEquals(10, Leveling.catacombs(4_385.0).level)
        assertEquals(9, Leveling.catacombs(4_384.0).level)
        assertEquals(50, Leveling.catacombs(569_809_640.0).level)
        assertEquals(50, Leveling.catacombs(2e9).level)
    }

    @Test
    fun slayerLevels() {
        assertEquals(0, Leveling.slayerLevel("zombie", 4))
        assertEquals(1, Leveling.slayerLevel("zombie", 5))
        assertEquals(2, Leveling.slayerLevel("spider", 25))
        assertEquals(1, Leveling.slayerLevel("spider", 24))
        assertEquals(3, Leveling.slayerLevel("wolf", 250))
        assertEquals(9, Leveling.slayerLevel("enderman", 5_000_000))
        assertEquals(5, Leveling.slayerLevel("vampire", 2_400))
        assertEquals(0, Leveling.slayerLevel("unknown", 1_000_000))
    }
}
