package com.skydex.shared.model

/** Skill caps and averages shared by the server (leveling) and the app (display order). */
object Skills {
    /**
     * Display order; max level of each skill. Also the list of real skills: Hypixel stores bookkeeping values such
     * as `SKILL_FORAGING_EXTRA_LEVEL_CAP` under the same prefix.
     */
    val CAPS: Map<String, Int> = linkedMapOf(
        "farming" to 60, "mining" to 60, "combat" to 60, "foraging" to 57,
        "fishing" to 50, "enchanting" to 60, "alchemy" to 50, "carpentry" to 50, "taming" to 60, "hunting" to 50,
        "runecrafting" to 25, "social" to 25,
    )

    /** Skills left out of the skill average, as on SkyCrypt. */
    val COSMETIC = setOf("runecrafting", "social")

    /** Mean of (level + progress, or level when maxed) over the 10 non-cosmetic skills; missing count 0. */
    fun average(skills: List<SkillLevel>): Double {
        val counted = CAPS.keys - COSMETIC
        val sum = skills.filter { it.name in counted }
            .sumOf { if (it.level < it.maxLevel) it.level + it.progress else it.level.toDouble() }
        return sum / counted.size
    }
}
