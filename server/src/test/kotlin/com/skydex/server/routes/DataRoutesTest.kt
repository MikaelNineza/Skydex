package com.skydex.server.routes

import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.testDatabase
import com.skydex.server.plugins.configureSerialization
import com.skydex.shared.model.Crop
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataRoutesTest {
    private val db = testDatabase()
    private val devices = DeviceRepository(db)
    private val now = 1_000.days.inWholeMilliseconds

    private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { configureSerialization() }
        routing {
            deviceRoutes(devices) { now }
            eventRoutes { now }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.putDevice(id: String, body: String) =
        client.put("/v1/devices/$id") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun registerAndUnregisterDevice() = routesTest {
        val registration = DeviceRegistration(
            fcmToken = "token",
            subscribedEvents = setOf(EventType.DARK_AUCTION),
            leadMinutes = 10,
        )

        val put = putDevice("install-1", Json.encodeToString(registration))
        assertEquals(HttpStatusCode.NoContent, put.status)
        assertEquals(registration, devices.find("install-1")?.registration)

        assertEquals(HttpStatusCode.NoContent, client.delete("/v1/devices/install-1").status)
        assertNull(devices.find("install-1"))
    }

    @Test
    fun unknownEventsAndCropsFromANewerAppAreDropped() = routesTest {
        val body = """{"fcmToken":"t","subscribedEvents":["DARK_AUCTION","SOME_FUTURE_EVENT"],""" +
            """"jacobCrops":["WHEAT","FUTURE_CROP"],"someNewField":true}"""

        assertEquals(HttpStatusCode.NoContent, putDevice("install-1", body).status)
        assertEquals(
            DeviceRegistration("t", subscribedEvents = setOf(EventType.DARK_AUCTION), jacobCrops = setOf(Crop.WHEAT)),
            devices.find("install-1")?.registration,
        )
    }

    @Test
    fun trackedFieldsFromAnOldAppAreIgnored() = routesTest {
        val body = """{"fcmToken":"t","subscribedEvents":["DARK_AUCTION"],""" +
            """"trackedUuid":"0f1e2d3c4b5a69788796a5b4c3d2e1f0","trackedProfileId":"not-even-a-uuid"}"""

        assertEquals(HttpStatusCode.NoContent, putDevice("install-1", body).status)
        assertEquals(
            DeviceRegistration("t", subscribedEvents = setOf(EventType.DARK_AUCTION)),
            devices.find("install-1")?.registration,
        )
    }

    @Test
    fun historyIsGone() = routesTest {
        val response = client.get("/v1/players/0f1e2d3c4b5a69788796a5b4c3d2e1f0/profiles/p/history?days=30")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun blankTokenIsAccepted() = routesTest {
        val body = """{"fcmToken":""}"""
        assertEquals(HttpStatusCode.NoContent, putDevice("install-1", body).status)
    }

    @Test
    fun invalidRegistrationsAreRejected() = routesTest {
        val invalid = listOf(
            """{"fcmToken":"t","leadMinutes":-1}""",
            // Unknown event names are dropped (a newer app), but the field must still be a list.
            """{"fcmToken":"t","subscribedEvents":"DARK_AUCTION"}""",
            """not json""",
        )
        for (body in invalid) {
            assertEquals(HttpStatusCode.BadRequest, putDevice("install-1", body).status, body)
        }
        assertEquals(HttpStatusCode.BadRequest, putDevice("bad%20id", """{"fcmToken":"t"}""").status)
    }

    @Test
    fun badQueriesAreRejected() = routesTest {
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/events?hours=soon").status)
    }
}
