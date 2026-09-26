package com.skydex.app.ui.profile

import androidx.compose.ui.graphics.Color
import com.skydex.app.ui.common.titleCase
import com.skydex.app.ui.theme.SkillMaxed
import com.skydex.app.ui.theme.SkillProgress
import com.skydex.shared.model.SkillLevel
import com.skydex.shared.model.Skills
import com.skydex.shared.model.SkyblockProfile
import java.util.Locale

// Pure display helpers for the profile screen, kept out of composables so they can be unit tested.

fun isMaxed(skill: SkillLevel): Boolean = skill.level >= skill.maxLevel

/** Gold when maxed, green while levelling; used for the level text and the bar. */
fun skillColor(skill: SkillLevel): Color = if (isMaxed(skill)) SkillMaxed else SkillProgress

/** Filled fraction of the skill bar: full when maxed. */
fun barProgress(skill: SkillLevel): Float = if (isMaxed(skill)) 1f else skill.progress.toFloat().coerceIn(0f, 1f)

/**
 * Every skill in [Skills.CAPS] order, so the grid stays even: skills the API didn't report show as level 0.
 * Skills we don't know are appended at the end.
 */
fun displaySkills(skills: List<SkillLevel>): List<SkillLevel> {
    val byName = skills.associateBy { it.name }
    val known = Skills.CAPS.map { (name, cap) -> byName[name] ?: SkillLevel(name, 0.0, 0, cap, 0.0) }
    return known + skills.filter { it.name !in Skills.CAPS }
}

/** 52.466 -> "52.47". */
fun formatSkillAverage(average: Double): String = String.format(Locale.ROOT, "%.2f", average)

/** "212 / 289". */
fun fairySoulsLabel(profile: SkyblockProfile): String = "${profile.fairySouls} / ${profile.fairySoulsTotal}"

/** Slayer boss names as shown in game, e.g. "zombie" -> "Revenant". */
fun slayerName(boss: String): String = when (boss) {
    "zombie" -> "Revenant"
    "spider" -> "Tarantula"
    "wolf" -> "Sven"
    "enderman" -> "Voidgloom"
    "blaze" -> "Inferno"
    "vampire" -> "Riftstalker"
    else -> boss.titleCase()
}
