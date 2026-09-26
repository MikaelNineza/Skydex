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
        assertNull(profile.rank)
        assertNull(profile.face)
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

    @Test
    fun rankAndFaceRoundTrip() {
        val face = List(64) { if (it % 2 == 0) 0xFF112233.toInt() else -1 }
        val profile = Json.decodeFromString<SkyblockProfile>(oldJson)
            .copy(rank = PlayerRank("MVP", "#33AEC3", "++", "#C43C3C"), face = face)
        val decoded = Json.decodeFromString<SkyblockProfile>(Json.encodeToString(profile))

        assertEquals(profile, decoded)
        assertEquals(PlayerRank("MVP", "#33AEC3", "++", "#C43C3C"), decoded.rank)
        assertEquals(face, decoded.face)
    }

    @Test
    fun missingRankAndFaceAreNotEncoded() {
        val json = Json.encodeToString(Json.decodeFromString<SkyblockProfile>(oldJson))
        assertTrue("rank" !in json && "face" !in json, json)
        assertEquals(
            PlayerRank("ADMIN", "#C43C3C"),
            Json.decodeFromString<PlayerRank>("""{"name":"ADMIN","color":"#C43C3C"}"""),
        )
    }

    @Test
    fun oldDeviceRegistrationWithTrackedFieldsDecodes() {
        // The server's JSON config; old apps still send the tracked profile.
        val server = Json { ignoreUnknownKeys = true }
        val body = """{"fcmToken":"t","trackedUuid":"u","trackedProfileId":"p","subscribedEvents":["DARK_AUCTION"],""" +
            """"leadMinutes":10}"""

        assertEquals(
            DeviceRegistration("t", subscribedEvents = setOf(EventType.DARK_AUCTION), leadMinutes = 10),
            server.decodeFromString<DeviceRegistration>(body),
        )
        assertTrue("tracked" !in Json.encodeToString(DeviceRegistration("t")))
    }
}
