package com.skydex.server.hypixel

import com.skydex.shared.model.PlayerRank
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RED = "#C43C3C"
private const val GOLD = "#D88F07"
private const val AQUA = "#33AEC3"
private const val GREEN = "#40BB40"
private const val DARK_GREEN = "#00AA00"
private const val DARK_AQUA = "#038D8D"
private const val BLUE = "#4444F3"
private const val PINK = "#E668C6"

class RankTest {
    private fun player(vararg fields: Pair<String, String>): JsonObject =
        JsonObject(fields.associate { (key, value) -> key to JsonPrimitive(value) })

    private fun rankOf(vararg fields: Pair<String, String>) = parseRank(player(*fields))

    @Test
    fun noRank() {
        assertNull(parseRank(null))
        assertNull(parseRank(JsonObject(emptyMap())))
        assertNull(rankOf("newPackageRank" to "NONE"))
        assertNull(rankOf("packageRank" to "NONE"))
        assertNull(rankOf("rank" to "NORMAL"))
        assertNull(rankOf("rank" to "NORMAL", "monthlyPackageRank" to "NONE", "newPackageRank" to "NONE"))
        assertNull(rankOf("newPackageRank" to "SOMETHING_NEW"))
    }

    @Test
    fun packageRanks() {
        assertEquals(PlayerRank("VIP", GREEN), rankOf("newPackageRank" to "VIP"))
        assertEquals(PlayerRank("VIP", GREEN, "+", GOLD), rankOf("newPackageRank" to "VIP_PLUS"))
        assertEquals(PlayerRank("MVP", AQUA), rankOf("newPackageRank" to "MVP"))
        assertEquals(PlayerRank("MVP", AQUA, "+", RED), rankOf("newPackageRank" to "MVP_PLUS"))
        assertEquals(
            PlayerRank("MVP", AQUA, "+", DARK_GREEN),
            rankOf("newPackageRank" to "MVP_PLUS", "rankPlusColor" to "DARK_GREEN"),
        )
        // VIP+'s plus is always gold, whatever rankPlusColor says.
        assertEquals(
            PlayerRank("VIP", GREEN, "+", GOLD),
            rankOf("newPackageRank" to "VIP_PLUS", "rankPlusColor" to "DARK_GREEN"),
        )
        // An unknown colour name falls back to the default.
        assertEquals(
            PlayerRank("MVP", AQUA, "+", RED),
            rankOf("newPackageRank" to "MVP_PLUS", "rankPlusColor" to "RAINBOW"),
        )
    }

    @Test
    fun legacyPackageRank() {
        assertEquals(PlayerRank("VIP", GREEN), rankOf("packageRank" to "VIP"))
        assertEquals(PlayerRank("MVP", AQUA, "+", RED), rankOf("packageRank" to "MVP_PLUS"))
        // newPackageRank wins over the legacy field.
        assertEquals(
            PlayerRank("MVP", AQUA, "+", RED),
            rankOf("packageRank" to "VIP", "newPackageRank" to "MVP_PLUS"),
        )
    }

    @Test
    fun superstar() {
        assertEquals(
            PlayerRank("MVP", GOLD, "++", RED),
            rankOf("monthlyPackageRank" to "SUPERSTAR", "newPackageRank" to "MVP_PLUS"),
        )
        assertEquals(
            PlayerRank("MVP", AQUA, "++", RED),
            rankOf("monthlyPackageRank" to "SUPERSTAR", "monthlyRankColor" to "AQUA", "newPackageRank" to "MVP_PLUS"),
        )
        assertEquals(
            PlayerRank("MVP", GOLD, "++", BLUE),
            rankOf("monthlyPackageRank" to "SUPERSTAR", "rankPlusColor" to "BLUE", "newPackageRank" to "MVP_PLUS"),
        )
        // An expired MVP++ is plain MVP+.
        assertEquals(
            PlayerRank("MVP", AQUA, "+", RED),
            rankOf("monthlyPackageRank" to "NONE", "newPackageRank" to "MVP_PLUS"),
        )
    }

    @Test
    fun staffRanksBeatMvpPlusPlus() {
        val superstar = arrayOf("monthlyPackageRank" to "SUPERSTAR", "newPackageRank" to "MVP_PLUS")
        val expected = mapOf(
            "ADMIN" to PlayerRank("ADMIN", RED),
            "OWNER" to PlayerRank("OWNER", RED),
            "GAME_MASTER" to PlayerRank("GM", DARK_GREEN),
            "MODERATOR" to PlayerRank("MOD", DARK_GREEN),
            "HELPER" to PlayerRank("HELPER", BLUE),
            "YOUTUBER" to PlayerRank("YOUTUBE", RED),
        )
        for ((rank, badge) in expected) {
            assertEquals(badge, rankOf("rank" to rank, *superstar), rank)
        }
        // A staff rank this server doesn't know falls through to the package rank.
        assertEquals(PlayerRank("MVP", GOLD, "++", RED), rankOf("rank" to "SOMETHING_NEW", *superstar))
    }

    @Test
    fun customPrefixWins() {
        assertEquals(
            PlayerRank("OWNER", RED),
            rankOf("prefix" to "§c[OWNER]", "rank" to "ADMIN", "monthlyPackageRank" to "SUPERSTAR"),
        )
        assertEquals(PlayerRank("BUILD TEAM", DARK_AQUA), rankOf("prefix" to "§3[BUILD TEAM]"))
        // The badge takes the first colour code, even when the text is another colour.
        assertEquals(PlayerRank("YOUTUBE", RED), rankOf("prefix" to "§c[§fYOUTUBE§c]"))
        assertEquals(PlayerRank("MVP", AQUA, "+", AQUA), rankOf("prefix" to "§b[MVP+]"))
        assertEquals(PlayerRank("PIG", PINK, "+++", AQUA), rankOf("prefix" to "§d[PIG§b+++§d]"))
        // Formatting codes (bold etc.) don't change the colour.
        assertEquals(PlayerRank("MOD", DARK_GREEN), rankOf("prefix" to "§2§l[MOD]"))
    }

    @Test
    fun overlongPrefixIsCapped() {
        val rank = rankOf("prefix" to "§c[" + "A".repeat(40) + "++++++]")!!
        assertEquals("A".repeat(16), rank.name)
        assertEquals("+++", rank.plus)
        assertEquals(RED, rank.color)
        assertEquals(RED, rank.plusColor)
    }

    @Test
    fun garbageIsNullOrSafe() {
        // Empty or unreadable prefixes fall through to the package rank.
        for (prefix in listOf("", "   ", "[]", "§c[]", "§c§l", "§")) {
            val rank = rankOf("prefix" to prefix, "newPackageRank" to "VIP")
            if (rank != PlayerRank("VIP", GREEN)) {
                // Anything else must still be a small, well-formed badge.
                assertTrue(rank != null && rank.name.isNotBlank() && rank.name.length <= 16, "$prefix -> $rank")
                assertTrue(rank.color.matches(Regex("#[0-9A-F]{6}")), "$prefix -> $rank")
            }
        }
        // Wrong JSON types are ignored rather than failing.
        val wrongTypes = buildJsonObject {
            put("prefix", 5)
            put("rank", true)
            put("monthlyPackageRank", 1.5)
            put("newPackageRank", "MVP")
        }
        assertEquals(PlayerRank("MVP", AQUA), parseRank(wrongTypes))
        val nested = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"prefix":{"a":1},"rank":["ADMIN"],"newPackageRank":null}""",
        ).jsonObject
        assertNull(parseRank(nested))
    }
}
