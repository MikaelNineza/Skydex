package com.skydex.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileSerializationTest {
    /** A profile as sent by servers (and cached by apps) from before fairySoulsTotal and skillAverage. */
    private val oldJson = """{"profileId":"p1","cuteName":"Mango","uuid":"u","username":"Tester","skyblockLevel":212.45,""" +
        """"purse":1.0,"fairySouls":238,"skills":[{"name":"farming","experience":5.0,"level":50,"maxLevel":60,""" +
        """"progress":0.0}],"slayers":[],"fetchedAt":5}"""

    @Test
    fun oldJsonDecodesWithDefaults() {
        val profile = Json.decodeFromString<SkyblockProfile>(oldJson)

        assertEquals(238, profile.fairySouls)
        assertEquals(289, profile.fairySoulsTotal)
        assertNull(profile.skillAverage)
    }

    @Test
    fun fairySoulsTotalIsAlwaysEncoded() {
        val profile = Json.decodeFromString<SkyblockProfile>(oldJson)
        val json = Json.encodeToString(profile)

        assertTrue("\"fairySoulsTotal\":289" in json, json)
        assertEquals(profile, Json.decodeFromString<SkyblockProfile>(json))
    }

    @Test
    fun newFieldsRoundTrip() {
        val profile = Json.decodeFromString<SkyblockProfile>(oldJson).copy(fairySoulsTotal = 300, skillAverage = 52.47)
        val decoded = Json.decodeFromString<SkyblockProfile>(Json.encodeToString(profile))

        assertEquals(300, decoded.fairySoulsTotal)
        assertEquals(52.47, decoded.skillAverage)
    }
}
