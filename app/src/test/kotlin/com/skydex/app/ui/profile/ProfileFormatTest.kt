package com.skydex.app.ui.profile

import androidx.compose.ui.graphics.Color
import com.skydex.app.ui.common.formatCoins
import com.skydex.app.sampleProfile
import com.skydex.shared.model.ApiSettings
import com.skydex.shared.model.Leveling
import com.skydex.shared.model.SkillLevel
import com.skydex.shared.model.SlayerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormatTest {
    private val gold = Color(0xFFCE9012)
    private val green = Color(0xFF00A63E)

    private fun farming(level: Int, progress: Double = 0.0) = SkillLevel("farming", 0.0, level, 60, progress)

    @Test
    fun `a skill is maxed from its max level up`() {
        assertFalse(isMaxed(farming(59, 0.99)))
        assertTrue(isMaxed(farming(60, 1.0)))
        assertTrue(isMaxed(farming(61)))
        // Caps differ per skill.
        assertTrue(isMaxed(SkillLevel("social", 0.0, 25, 25, 1.0)))
        assertFalse(isMaxed(SkillLevel("foraging", 0.0, 50, 57, 0.0)))
    }

    @Test
    fun `maxed skills are gold and levelling ones green`() {
        assertEquals(green, skillColor(farming(59, 0.99)))
        assertEquals(gold, skillColor(farming(60, 1.0)))
        assertEquals(gold, skillColor(farming(61)))
    }

    @Test
    fun `bar progress is full when maxed and clamped otherwise`() {
        assertEquals(0.25f, barProgress(farming(20, 0.25)), 0f)
        assertEquals(1f, barProgress(farming(60, 0.0)), 0f)
        assertEquals(1f, barProgress(farming(20, 1.5)), 0f)
        assertEquals(0f, barProgress(farming(20, -0.5)), 0f)
    }

    @Test
    fun `display skills follow the cap order, pad missing ones and append unknown ones`() {
        val combat = SkillLevel("combat", 1e6, 30, 60, 0.4)
        val farming = farming(60, 1.0)
        val mystery = SkillLevel("mystery", 1.0, 3, 10, 0.1)

        val shown = displaySkills(listOf(mystery, combat, farming))

        assertEquals(
            listOf(
                "farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "carpentry", "taming",
                "hunting", "runecrafting", "social", "mystery",
            ),
            shown.map { it.name },
        )
        assertEquals(farming, shown[0])
        assertEquals(combat, shown[2])
        assertEquals(SkillLevel("mining", 0.0, 0, 60, 0.0), shown[1])
        assertEquals(SkillLevel("foraging", 0.0, 0, 57, 0.0), shown[3])
        assertEquals(SkillLevel("social", 0.0, 0, 25, 0.0), shown[11])
        assertEquals(mystery, shown.last())
        // An even count, so the two-column grid has no gap.
        assertEquals(12, displaySkills(emptyList()).size)
    }

    @Test
    fun `fairy souls show collected over total`() {
        assertEquals("240 / 289", fairySoulsLabel(sampleProfile))
        assertEquals("0 / 300", fairySoulsLabel(sampleProfile.copy(fairySouls = 0, fairySoulsTotal = 300)))
    }

    @Test
    fun `skill average has two decimals`() {
        assertEquals("52.47", formatSkillAverage(52.466))
        assertEquals("0.00", formatSkillAverage(0.0))
        assertEquals("60.00", formatSkillAverage(60.0))
    }

    @Test
    fun `slayer bosses use their in game names`() {
        assertEquals("Revenant", slayerName("zombie"))
        assertEquals("Tarantula", slayerName("spider"))
        assertEquals("Sven", slayerName("wolf"))
        assertEquals("Voidgloom", slayerName("enderman"))
        assertEquals("Inferno", slayerName("blaze"))
        assertEquals("Riftstalker", slayerName("vampire"))
        assertEquals("Newboss", slayerName("newboss"))
    }

    @Test
    fun `skills api off follows apiDisabled, else an empty skill list`() {
        val some = sampleProfile
        val none = sampleProfile.copy(skills = emptyList())
        // Servers that predate apiDisabled: infer from the skills.
        assertFalse(skillsApiOff(some))
        assertTrue(skillsApiOff(none))
        // Otherwise apiDisabled decides, whatever the skills.
        assertTrue(skillsApiOff(some.copy(apiDisabled = listOf(ApiSettings.SKILLS))))
        assertTrue(skillsApiOff(none.copy(apiDisabled = listOf(ApiSettings.BANKING, ApiSettings.SKILLS))))
        assertFalse(skillsApiOff(none.copy(apiDisabled = emptyList())))
        assertFalse(skillsApiOff(some.copy(apiDisabled = listOf(ApiSettings.BANKING, ApiSettings.INVENTORY))))
    }

    @Test
    fun `skill average label says API off or shows the average`() {
        assertEquals("API off", skillAverageLabel(sampleProfile.copy(apiDisabled = listOf(ApiSettings.SKILLS))))
        assertEquals("API off", skillAverageLabel(sampleProfile.copy(skills = emptyList(), skillAverage = null)))
        assertEquals("52.47", skillAverageLabel(sampleProfile.copy(skillAverage = 52.466, apiDisabled = emptyList())))
        // Nothing earned yet but the API is on: 0.00, not "API off".
        assertEquals("0.00", skillAverageLabel(sampleProfile.copy(skills = emptyList(), skillAverage = 0.0, apiDisabled = emptyList())))
        // Old servers send no average: computed from the skills (farming 40.5 over the 10 averaged skills).
        assertEquals("4.05", skillAverageLabel(sampleProfile.copy(skillAverage = null)))
    }

    @Test
    fun `xp lines show progress in the level and towards the max`() {
        assertEquals(XpLine("0/4.3M", "55.2M/111.7M"), xpLine(Leveling.skill("farming", 55_172_425.0)))
        assertEquals(XpLine("75/125", "125/111.7M"), xpLine(Leveling.skill("combat", 125.0)))
        assertEquals(XpLine("0/50", "0/111.7M"), xpLine(SkillLevel("mining", 0.0, 0, 60, 0.0)))
        assertEquals(XpLine("0/1.9K", "4.4K/569.8M"), xpLine(Leveling.catacombs(4_385.0)))
        // Every padded tile in the grid has a line.
        displaySkills(emptyList()).forEach { assertTrue(it.name, xpLine(it) != null) }
    }

    @Test
    fun `maxed skills show MAX and their total`() {
        assertEquals(XpLine("MAX", "94.5K"), xpLine(Leveling.skill("runecrafting", 94_450.0)))
        assertEquals(XpLine("MAX", "111.7M"), xpLine(Leveling.skill("mining", 111_672_425.0)))
        // Catacombs overflow beyond 50 is in the total.
        assertEquals(XpLine("MAX", "2B"), xpLine(Leveling.catacombs(2e9)))
        assertNull(xpLine(SkillLevel("mystery", 100.0, 1, 10, 0.5)))
    }

    @Test
    fun `xp just short of a level never shows as reached`() {
        assertEquals("4.2M", formatCoinsFloor(4_299_999.0, 4_300_000.0))
        assertNotEquals(formatCoins(4_300_000.0), formatCoinsFloor(4_299_999.0, 4_300_000.0))
        assertEquals("399.9K", formatCoinsFloor(399_999.0, 400_000.0))
        assertEquals("999.9K", formatCoinsFloor(999_999.0, 1_000_000.0))
        assertEquals("124", formatCoinsFloor(124.6, 125.0))
        // formatCoins rounds 999.6 to "1K", the target itself.
        assertEquals("999", formatCoinsFloor(999.6, 1_000.0))
        // Otherwise it rounds as usual, and a reached or passed target shows as is.
        assertEquals("1.3M", formatCoinsFloor(1_250_000.0, 4_300_000.0))
        assertEquals("4.3M", formatCoinsFloor(4_300_000.0, 4_300_000.0))
        assertEquals("4.3M", formatCoinsFloor(4_300_001.0, 4_300_000.0))
        assertEquals("0", formatCoinsFloor(0.0, 50.0))

        // In an xp line: 1 XP short of level 51 farming, and 1 XP short of max.
        val almost51 = xpLine(Leveling.skill("farming", 55_172_425.0 + 4_299_999))!!
        assertEquals("4.2M/4.3M", almost51.level)
        val almostMax = xpLine(Leveling.skill("farming", 111_672_424.0))!!
        assertEquals("6.9M/7M", almostMax.level)
        assertEquals("111.6M/111.7M", almostMax.total)
        // 999.6 of level 7's 1,000 (after 1,925 for levels 1 to 6).
        assertEquals("999/1K", xpLine(Leveling.skill("combat", 1_925.0 + 999.6))!!.level)
    }

    @Test
    fun `xp line descriptions read as sentences`() {
        assertEquals("75 of 125 XP this level, 125 of 111.7M total", XpLine("75/125", "125/111.7M").description)
        assertEquals("Max level, 94.5K XP total", XpLine("MAX", "94.5K").description)
        assertEquals(
            "0 of 4.3M XP this level, 55.2M of 111.7M total",
            xpLine(Leveling.skill("farming", 55_172_425.0))!!.description,
        )
    }

    @Test
    fun `slayer xp shows the next level and the max`() {
        assertEquals("250/1.5K XP" to "max 1M", slayerXpLabel(SlayerLevel("wolf", 250, 3)))
        assertEquals("0/5 XP" to "max 1M", slayerXpLabel(SlayerLevel("spider", 0, 0)))
        assertEquals("1.2K/2.4K XP" to "max 2.4K", slayerXpLabel(SlayerLevel("vampire", 1_200, 4)))
        // Numerators are floored so they never look like the next level.
        assertEquals("399.9K/400K XP" to "max 1M", slayerXpLabel(SlayerLevel("zombie", 399_999, 7)))
        assertEquals("999.9K/1M XP" to "max 1M", slayerXpLabel(SlayerLevel("zombie", 999_999, 8)))
        // Maxed, beyond max, or a boss we don't know: just the XP.
        assertEquals("1M XP" to null, slayerXpLabel(SlayerLevel("zombie", 1_000_000, 9)))
        assertEquals("2.5M XP" to null, slayerXpLabel(SlayerLevel("blaze", 2_500_000, 9)))
        assertEquals("2.4K XP" to null, slayerXpLabel(SlayerLevel("vampire", 2_400, 5)))
        assertEquals("123.4K XP" to null, slayerXpLabel(SlayerLevel("newboss", 123_400, 3)))
    }
}
