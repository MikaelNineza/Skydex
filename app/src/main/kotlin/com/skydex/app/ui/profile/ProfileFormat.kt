package com.skydex.app.ui.profile

import androidx.compose.ui.graphics.Color
import com.skydex.app.ui.common.formatCoins
import com.skydex.app.ui.common.titleCase
import com.skydex.app.ui.theme.SkillMaxed
import com.skydex.app.ui.theme.SkillProgress
import com.skydex.shared.model.ApiSettings
import com.skydex.shared.model.Leveling
import com.skydex.shared.model.SkillLevel
import com.skydex.shared.model.Skills
import com.skydex.shared.model.SkyblockProfile
import com.skydex.shared.model.SlayerLevel
import java.util.Locale
import kotlin.math.floor

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

/**
 * True when the player has the Skills API off. Servers that predate [SkyblockProfile.apiDisabled] don't say, so then
 * an empty skill list is taken to mean it's off.
 */
fun skillsApiOff(profile: SkyblockProfile): Boolean =
    profile.apiDisabled?.contains(ApiSettings.SKILLS) ?: profile.skills.isEmpty()

/** The "Skill avg" value: "API off", or the average (computed from the skills for servers that don't send it). */
fun skillAverageLabel(profile: SkyblockProfile): String =
    if (skillsApiOff(profile)) "API off" else formatSkillAverage(profile.skillAverage ?: Skills.average(profile.skills))

/** The XP line under a skill bar: [level] is XP into the current level, [total] is XP towards the max. */
data class XpLine(val level: String, val total: String) {
    /** For screen readers: "75 of 125 XP this level, 125 of 111.7M total", or "Max level, 55.2M XP total". */
    val description: String
        get() = if (level == "MAX") {
            "Max level, $total XP total"
        } else {
            "${level.replace("/", " of ")} XP this level, ${total.replace("/", " of ")} total"
        }
}

/** e.g. ("75/125", "125/111.7M"), or ("MAX", "55.2M") when maxed; null for skills we don't have a table for. */
fun xpLine(skill: SkillLevel): XpLine? {
    val xp = Leveling.progress(skill) ?: return null
    val next = xp.forNextLevel ?: return XpLine("MAX", formatCoins(xp.total))
    return XpLine(
        "${formatCoinsFloor(xp.inLevel, next.toDouble())}/${formatCoins(next.toDouble())}",
        "${formatCoinsFloor(xp.total, xp.forMax.toDouble())}/${formatCoins(xp.forMax.toDouble())}",
    )
}

/** Slayer XP towards the next level and the max, e.g. ("123.4K/400K XP", "max 1M"), or ("1M XP", null) when maxed. */
fun slayerXpLabel(slayer: SlayerLevel): Pair<String, String?> {
    val xp = formatCoins(slayer.experience.toDouble())
    val next = Leveling.slayerNextLevelXp(slayer.boss, slayer.experience) ?: return "$xp XP" to null
    val max = Leveling.slayerMaxXp(slayer.boss) ?: return "$xp XP" to null
    val progress = formatCoinsFloor(slayer.experience.toDouble(), next.toDouble())
    return "$progress/${formatCoins(next.toDouble())} XP" to "max ${formatCoins(max.toDouble())}"
}

/**
 * [formatCoins] for XP towards [target], rounded down when rounding would make it look like the target was reached:
 * 4,299,999 of 4.3M shows as "4.2M", not "4.3M".
 */
fun formatCoinsFloor(value: Double, target: Double): String {
    val shown = formatCoins(value)
    if (value >= target || shown != formatCoins(target)) return shown
    var scale = 1.0
    while (scale < 1e12 && value >= scale * 1000) scale *= 1000
    // formatCoins shows whole numbers below 1000 and tenths of K, M, ... above.
    val step = if (scale == 1.0) 1.0 else scale / 10
    return formatCoins(floor(value / step) * step)
}

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
