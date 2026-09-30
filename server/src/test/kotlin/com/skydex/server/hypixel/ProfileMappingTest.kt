package com.skydex.server.hypixel

import com.skydex.shared.model.ApiSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val UUID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"

class ProfileMappingTest {
    /** A profile whose only member is [UUID], with [member] and top-level [extra] JSON fields spliced in. */
    private fun profile(member: String, extra: String = ""): JsonObject {
        val top = if (extra.isEmpty()) "" else "$extra,"
        return Json.parseToJsonElement(
            """{"profile_id":"p","cute_name":"Mango",$top"members":{"$UUID":{$member}}}""",
        ).jsonObject
    }

    private fun map(profile: JsonObject) = assertNotNull(toSkyblockProfile(profile, UUID, "Tester", fetchedAt = 1))

    @Test
    fun emptyExperienceMeansSkillsOnWithNoXp() {
        // Skills API on but nothing earned yet: the object is there, just empty.
        val mapped = map(profile(""""player_data":{"experience":{}}"""))

        assertEquals(emptyList(), mapped.skills)
        assertEquals(0.0, mapped.skillAverage)
        assertEquals(
            listOf(ApiSettings.BANKING, ApiSettings.COLLECTIONS, ApiSettings.INVENTORY),
            mapped.apiDisabled,
        )
    }

    @Test
    fun missingExperienceMeansSkillsOff() {
        val mapped = map(profile(""""player_data":{"visited_zones":["hub"]}"""))

        assertEquals(emptyList(), mapped.skills)
        assertNull(mapped.skillAverage)
        assertEquals(ApiSettings.SKILLS, mapped.apiDisabled?.first())
        // No player_data at all is the same.
        assertEquals(ApiSettings.SKILLS, map(profile("")).apiDisabled?.first())
    }

    @Test
    fun presentSectionsAreNotReportedOff() {
        val mapped = map(
            profile(
                """"player_data":{"experience":{"SKILL_COMBAT":125.0}},"collection":{"WHEAT":5},""" +
                    """"inventory":{"inv_contents":{"type":0,"data":""}}""",
                extra = """"banking":{"balance":12.5}""",
            ),
        )

        assertEquals(emptyList(), mapped.apiDisabled)
        assertEquals(12.5, mapped.bankBalance)
        assertNotNull(mapped.skillAverage)
    }

    @Test
    fun eachGatedSectionIsReportedOnItsOwn() {
        val all = mapOf(
            "experience" to """"player_data":{"experience":{}}""",
            "collection" to """"collection":{}""",
            "inventory" to """"inventory":{}""",
        )
        fun without(key: String) = all.filterKeys { it != key }.values.joinToString(",")
        val banking = """"banking":{"balance":0}"""

        assertEquals(listOf(ApiSettings.SKILLS), map(profile(without("experience"), banking)).apiDisabled)
        assertEquals(listOf(ApiSettings.COLLECTIONS), map(profile(without("collection"), banking)).apiDisabled)
        assertEquals(listOf(ApiSettings.INVENTORY), map(profile(without("inventory"), banking)).apiDisabled)
        assertEquals(listOf(ApiSettings.BANKING), map(profile(all.values.joinToString(","))).apiDisabled)
        // A banking object without a balance is still Banking on (the balance just defaults to null).
        val noBalance = map(profile(all.values.joinToString(","), """"banking":{}"""))
        assertEquals(emptyList(), noBalance.apiDisabled)
        assertNull(noBalance.bankBalance)
    }
}
