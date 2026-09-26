package com.skydex.app.ui.profile

import androidx.compose.ui.graphics.Color
import com.skydex.app.sampleProfile
import com.skydex.shared.model.SkillLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
