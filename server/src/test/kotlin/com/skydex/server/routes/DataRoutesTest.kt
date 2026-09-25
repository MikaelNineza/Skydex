package com.skydex.server.routes

import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.SnapshotRepository
import com.skydex.server.db.testDatabase
import com.skydex.server.plugins.configureSerialization
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import com.skydex.shared.model.StatPoint
import com.skydex.shared.model.StatsHistory
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
    private val snapshots = SnapshotRepository(db)
    private val now = 1_000.days.inWholeMilliseconds

    private val uuid = "0123456789abcdef0123456789abcdef"
    private val profileId = "fedcba98-7654-3210-fedc-ba9876543210"

    private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { configureSerialization() }
        routing {
            deviceRoutes(devices) { now }
            historyRoutes(snapshots) { now }
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
            trackedUuid = uuid,
            trackedProfileId = profileId,
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
    fun trackingOnlyDeviceNeedsNoToken() = routesTest {
        val body = """{"fcmToken":"","trackedUuid":"$uuid","trackedProfileId":"$profileId"}"""
        assertEquals(HttpStatusCode.NoContent, putDevice("install-1", body).status)
    }

    @Test
    fun invalidRegistrationsAreRejected() = routesTest {
        val invalid = listOf(
            """{"fcmToken":"t","leadMinutes":-1}""",
            """{"fcmToken":"t","trackedUuid":"$uuid"}""",
            """{"fcmToken":"t","trackedUuid":"not-a-uuid","trackedProfileId":"$profileId"}""",
            """{"fcmToken":"t","subscribedEvents":["NOT_AN_EVENT"]}""",
            """not json""",
        )
        for (body in invalid) {
            assertEquals(HttpStatusCode.BadRequest, putDevice("install-1", body).status, body)
        }
        assertEquals(HttpStatusCode.BadRequest, putDevice("bad%20id", """{"fcmToken":"t"}""").status)
    }

    @Test
    fun historyReturnsTheRequestedDaysOldestFirst() = routesTest {
        val point = StatPoint(0, 1.0, 2.0, null, 3.0, null, 4)
        for (daysAgo in listOf(1, 40, 10, 400)) {
            snapshots.insert(uuid, profileId, point.copy(takenAt = now - daysAgo.days.inWholeMilliseconds))
        }

        suspend fun history(query: String): StatsHistory {
            val response = client.get("/v1/players/$uuid/profiles/$profileId/history$query")
            assertEquals(HttpStatusCode.OK, response.status)
            return Json.decodeFromString(response.bodyAsText())
        }
        fun StatsHistory.daysAgo() = points.map { ((now - it.takenAt) / 1.days.inWholeMilliseconds).toInt() }

        assertEquals(listOf(10, 1), history("").daysAgo())
        assertEquals(listOf(40, 10, 1), history("?days=50").daysAgo())
        assertEquals(listOf(40, 10, 1), history("?days=100000").daysAgo())
        assertEquals(listOf(1), history("?days=0").daysAgo())
    }

    @Test
    fun badPathsAndQueriesAreRejected() = routesTest {
        val base = "/v1/players/$uuid/profiles/$profileId/history"
        assertEquals(HttpStatusCode.BadRequest, client.get("$base?days=abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/players/ABC/profiles/$profileId/history").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/players/$uuid/profiles/x/history").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/events?hours=soon").status)
    }
}
