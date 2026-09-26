package com.skydex.server.hypixel

import com.skydex.shared.model.PlayerRank
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Hypixel's colour names (as in `rankPlusColor`) to Minecraft § colour codes. */
internal val MC_COLOR_CODES: Map<String, Char> = mapOf(
    "BLACK" to '0',
    "DARK_BLUE" to '1',
    "DARK_GREEN" to '2',
    "DARK_AQUA" to '3',
    "DARK_RED" to '4',
    "DARK_PURPLE" to '5',
    "GOLD" to '6',
    "GRAY" to '7',
    "DARK_GRAY" to '8',
    "BLUE" to '9',
    "GREEN" to 'a',
    "AQUA" to 'b',
    "RED" to 'c',
    "LIGHT_PURPLE" to 'd',
    "YELLOW" to 'e',
    "WHITE" to 'f',
)

/** Colour codes to SkyCrypt's badge backgrounds: darker than the in-game colours so white text stays readable. */
internal val CODE_HEX: Map<Char, String> = mapOf(
    '0' to "#000000",
    '1' to "#0B277A",
    '2' to "#00AA00",
    '3' to "#038D8D",
    '4' to "#920909",
    '5' to "#A305A3",
    '6' to "#D88F07",
    '7' to "#636363",
    '8' to "#2F2F2F",
    '9' to "#4444F3",
    'a' to "#40BB40",
    'b' to "#33AEC3",
    'c' to "#C43C3C",
    'd' to "#E668C6",
    'e' to "#EFC721",
    'f' to "#929292",
)

/**
 * The rank badge for a raw `/v2/player` object, or null for players without one. Precedence follows SkyCrypt: a
 * custom `prefix`, then a staff `rank`, then MVP++ (`monthlyPackageRank`), then `newPackageRank` or the legacy
 * `packageRank`. Malformed data gives null rather than failing the profile.
 */
internal fun parseRank(player: JsonObject?): PlayerRank? = runCatching {
    if (player == null) return@runCatching null
    fun field(name: String) = (player[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun code(colorField: String, default: Char) = field(colorField)?.let { MC_COLOR_CODES[it] } ?: default

    field("prefix")?.takeIf { it.isNotBlank() }?.let(::parsePrefix)?.let { return@runCatching it }

    when (field("rank")) {
        "OWNER" -> return@runCatching rank("OWNER", 'c')
        "ADMIN" -> return@runCatching rank("ADMIN", 'c')
        "GAME_MASTER" -> return@runCatching rank("GM", '2')
        "MODERATOR" -> return@runCatching rank("MOD", '2')
        "HELPER" -> return@runCatching rank("HELPER", '9')
        "YOUTUBER" -> return@runCatching rank("YOUTUBE", 'c')
    }

    val plusCode = code("rankPlusColor", 'c')
    if (field("monthlyPackageRank") == "SUPERSTAR") {
        return@runCatching rank("MVP", code("monthlyRankColor", '6'), "++", plusCode)
    }
    when (field("newPackageRank") ?: field("packageRank")) {
        "MVP_PLUS" -> rank("MVP", 'b', "+", plusCode)
        "MVP" -> rank("MVP", 'b')
        "VIP_PLUS" -> rank("VIP", 'a', "+", '6')
        "VIP" -> rank("VIP", 'a')
        else -> null
    }
}.getOrNull()

private fun rank(name: String, code: Char, plus: String? = null, plusCode: Char? = null) =
    PlayerRank(name, CODE_HEX.getValue(code), plus, plusCode?.let(CODE_HEX::getValue))

/**
 * A custom prefix like "§c[OWNER]" or "§d[PIG§b+++§d]". Like SkyCrypt, the badge takes the first colour code in the
 * prefix; brackets are dropped and a trailing run of pluses becomes the plus segment, in the colour of its first plus.
 * Output is capped so a long prefix can't stretch the badge.
 */
private fun parsePrefix(prefix: String): PlayerRank? {
    val text = StringBuilder()
    val codes = StringBuilder() // The colour of each character in text.
    var first: Char? = null
    var current = '7'
    var i = 0
    while (i < prefix.length) {
        val c = prefix[i]
        if (c == '§' && i + 1 < prefix.length) {
            when (val format = prefix[i + 1].lowercaseChar()) {
                in CODE_HEX -> {
                    current = format
                    if (first == null) first = format
                }
                'r' -> current = 'f'
                // k-o are obfuscated/bold/strikethrough/underline/italic: no colour change.
            }
            i += 2
            continue
        }
        if (c != '[' && c != ']') {
            text.append(c)
            codes.append(current)
        }
        i++
    }
    val trimmed = text.toString().trimEnd()
    val start = trimmed.length - trimmed.trimStart().length
    val plusStart = trimmed.trimEnd('+').length
    val name = trimmed.substring(start, maxOf(start, plusStart)).trim()
    val nameCode = first ?: '7'
    return when {
        name.isEmpty() -> trimmed.trim().takeIf { it.isNotEmpty() }?.let { rank(it.take(MAX_NAME), nameCode) }
        plusStart < trimmed.length ->
            rank(name.take(MAX_NAME), nameCode, trimmed.substring(plusStart).take(MAX_PLUS), codes[plusStart])
        else -> rank(name.take(MAX_NAME), nameCode)
    }
}

private const val MAX_NAME = 16
private const val MAX_PLUS = 3
