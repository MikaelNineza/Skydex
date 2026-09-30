package com.skydex.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun progress(name: String, xp: Double) =
        Leveling.progress(if (name == "catacombs") Leveling.catacombs(xp) else Leveling.skill(name, xp))

    @Test
    fun xpProgressIntoLevelAndTowardsMax() {
        // Exactly level 50: nothing into 51 yet, which takes 4.3M.
        assertEquals(Leveling.XpProgress(0.0, 4_300_000, 55_172_425.0, 111_672_425), progress("farming", 55_172_425.0))
        // 50 for level 1, then 75 of the 125 for level 2.
        assertEquals(Leveling.XpProgress(75.0, 125, 125.0, 111_672_425), progress("combat", 125.0))
        assertEquals(Leveling.XpProgress(0.0, 50, 0.0, 111_672_425), progress("combat", 0.0))
        // Foraging's max is level 57.
        val foraging = progress("foraging", 10.0)!!
        assertEquals(Leveling.SKILL_XP.take(57).sum(), foraging.forMax)
        assertEquals(50L, foraging.forNextLevel)
        // Social and runecrafting use their own tables.
        assertEquals(Leveling.XpProgress(0.0, 150, 150.0, 272_800), progress("social", 150.0))
    }

    @Test
    fun maxedXpProgressHasNoNextLevel() {
        val runecrafting = progress("runecrafting", 94_450.0)!!
        assertNull(runecrafting.forNextLevel)
        assertEquals(94_450.0, runecrafting.total)
        assertEquals(94_450L, runecrafting.forMax)
        assertEquals(0.0, runecrafting.inLevel)
        // One XP short of max: 19,049 of the last level's 19,050.
        assertEquals(Leveling.XpProgress(19_049.0, 19_050, 94_449.0, 94_450), progress("runecrafting", 94_449.0))

        // Catacombs overflow beyond 50 counts in the total only.
        val catacombs = progress("catacombs", 2e9)!!
        assertNull(catacombs.forNextLevel)
        assertEquals(2e9, catacombs.total)
        assertEquals(569_809_640L, catacombs.forMax)
        assertEquals(2e9 - 569_809_640, catacombs.inLevel)
        // Level 10 exactly: 0 of the 1,890 for level 11.
        assertEquals(Leveling.XpProgress(0.0, 1_890, 4_385.0, 569_809_640), progress("catacombs", 4_385.0))
    }

    @Test
    fun xpProgressFollowsTheReportedMaxLevel() {
        // A skill reported with a lower cap (e.g. farming before Anita's extra levels) is maxed at that cap.
        val farming = Leveling.progress(SkillLevel("farming", 55_172_425.0, 50, 50, 1.0))!!
        assertNull(farming.forNextLevel)
        assertEquals(55_172_425L, farming.forMax)
    }

    @Test
    fun unknownSkillsHaveNoXpProgress() {
        assertNull(Leveling.progress(SkillLevel("mystery", 100.0, 1, 10, 0.5)))
        assertNull(Leveling.table("mystery"))
        assertEquals(Leveling.CATACOMBS_XP, Leveling.table("catacombs"))
        assertEquals(Leveling.RUNECRAFTING_XP, Leveling.table("runecrafting"))
        assertEquals(Leveling.SOCIAL_XP, Leveling.table("social"))
        assertEquals(Leveling.SKILL_XP.take(57), Leveling.table("foraging"))
        assertEquals(Leveling.SKILL_XP, Leveling.table("farming"))
    }

    @Test
    fun slayerNextAndMaxXp() {
        assertEquals(5L, Leveling.slayerNextLevelXp("zombie", 0))
        // Reaching a threshold moves on to the next one.
        assertEquals(15L, Leveling.slayerNextLevelXp("zombie", 5))
        assertEquals(400_000L, Leveling.slayerNextLevelXp("zombie", 399_999))
        assertEquals(1_500L, Leveling.slayerNextLevelXp("wolf", 250))
        assertNull(Leveling.slayerNextLevelXp("zombie", 1_000_000))
        assertNull(Leveling.slayerNextLevelXp("vampire", 2_400))
        assertEquals(2_400L, Leveling.slayerNextLevelXp("vampire", 2_399))
        assertNull(Leveling.slayerNextLevelXp("unknown", 1))

        assertEquals(1_000_000L, Leveling.slayerMaxXp("zombie"))
        assertEquals(2_400L, Leveling.slayerMaxXp("vampire"))
        assertNull(Leveling.slayerMaxXp("unknown"))
    }
}
