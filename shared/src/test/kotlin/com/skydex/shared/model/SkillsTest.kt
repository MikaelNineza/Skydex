package com.skydex.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SkillsTest {
    private fun skill(name: String, level: Int, progress: Double = 0.0, maxLevel: Int = Skills.CAPS[name] ?: 50) =
        SkillLevel(name, 0.0, level, maxLevel, progress)

    @Test
    fun maxedSkillCountsItsLevelNotTheNextOne() {
        // Maxed skills report progress 1.0; adding it would count farming as 61.
        assertEquals(6.0, Skills.average(listOf(skill("farming", 60, 1.0))), 1e-9)
        assertEquals(5.0, Skills.average(listOf(skill("fishing", 50, 1.0))), 1e-9)
    }

    @Test
    fun levellingSkillAddsItsProgress() {
        assertEquals(1.25, Skills.average(listOf(skill("combat", 12, 0.5))), 1e-9)
    }

    @Test
    fun missingSkillsCountAsZeroSoTheDivisorIsAlwaysTen() {
        assertEquals(2.0, Skills.average(listOf(skill("mining", 10), skill("combat", 10))), 1e-9)
    }

    @Test
    fun cosmeticAndUnknownSkillsAreIgnored() {
        val skills = listOf(
            skill("runecrafting", 25, 1.0),
            skill("social", 20, 0.5),
            skill("catacombs", 40, 0.3),
            skill("dancing", 10, 0.0),
        )
        assertEquals(0.0, Skills.average(skills), 1e-9)
        assertEquals(setOf("runecrafting", "social"), Skills.COSMETIC)
    }

    @Test
    fun emptyIsZero() {
        assertEquals(0.0, Skills.average(emptyList()), 1e-9)
    }

    @Test
    fun allTwelveSkills() {
        val skills = listOf(
            skill("farming", 60, 1.0), // 60
            skill("mining", 55, 0.5), // 55.5
            skill("combat", 50, 0.25), // 50.25
            skill("foraging", 57, 1.0), // 57
            skill("fishing", 40, 0.0), // 40
            skill("enchanting", 60, 1.0), // 60
            skill("alchemy", 50, 1.0), // 50
            skill("carpentry", 49, 0.75), // 49.75
            skill("taming", 58, 0.2), // 58.2
            skill("hunting", 10, 0.0), // 10
            skill("runecrafting", 25, 1.0),
            skill("social", 25, 1.0),
        )
        assertEquals(490.7 / 10, Skills.average(skills), 1e-9)
    }

    @Test
    fun capsListTwelveSkillsInDisplayOrder() {
        assertEquals(
            listOf(
                "farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "carpentry", "taming",
                "hunting", "runecrafting", "social",
            ),
            Skills.CAPS.keys.toList(),
        )
        assertEquals(57, Skills.CAPS["foraging"])
        assertEquals(25, Skills.CAPS["social"])
    }
}
