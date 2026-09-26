package com.skydex.shared.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LenientSerializersTest {
    @Test
    fun unknownEventNamesAndCropsAreDropped() {
        val body = """{"fcmToken":"t","subscribedEvents":["DARK_AUCTION","FUTURE_EVENT","JACOBS_CONTEST"],""" +
            """"jacobCrops":["WHEAT","SPACE_POTATO"]}"""

        val registration = Json.decodeFromString<DeviceRegistration>(body)

        assertEquals(setOf(EventType.DARK_AUCTION, EventType.JACOBS_CONTEST), registration.subscribedEvents)
        assertEquals(setOf(Crop.WHEAT), registration.jacobCrops)
    }

    @Test
    fun registrationRoundTrips() {
        val registration = DeviceRegistration(
            fcmToken = "t",
            subscribedEvents = EventType.entries.toSet(),
            leadMinutes = 10,
            jacobCrops = Crop.entries.toSet(),
        )
        val json = Json.encodeToString(registration)

        assertEquals(registration, Json.decodeFromString<DeviceRegistration>(json))
        // Encoded by enum name, like the default serializer.
        assertEquals(true, "\"COCOA_BEANS\"" in json)
        assertEquals(DeviceRegistration("t"), Json.decodeFromString<DeviceRegistration>("""{"fcmToken":"t"}"""))
    }

    @Test
    fun contestCropsKeepOrderAndDropUnknown() {
        val contest = Json.decodeFromString<JacobContest>("""{"startsAt":5,"crops":["MELON","NEW_CROP","CACTUS"]}""")
        assertEquals(JacobContest(5, listOf(Crop.MELON, Crop.CACTUS)), contest)

        val full = JacobContest(1, listOf(Crop.WILD_ROSE, Crop.SUGAR_CANE, Crop.NETHER_WART))
        assertEquals(full, Json.decodeFromString<JacobContest>(Json.encodeToString(full)))
    }

    @Test
    fun wrongShapeStillFails() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<DeviceRegistration>("""{"fcmToken":"t","subscribedEvents":"DARK_AUCTION"}""")
        }
    }

    @Test
    fun cropDisplayNames() {
        assertEquals(Crop.COCOA_BEANS, Crop.fromDisplayName("Cocoa Beans"))
        assertEquals(Crop.WILD_ROSE, Crop.fromDisplayName("Wild Rose"))
        assertEquals(null, Crop.fromDisplayName("cocoa beans"))
        assertEquals(13, Crop.entries.size)
    }
}
