package com.skydex.app.data.remote

import com.skydex.app.FakeServer
import com.skydex.app.respondJson
import com.skydex.app.samplePlayer
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import com.skydex.shared.model.StatsHistory
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SkydexApiTest {
    private val server = FakeServer()

    @Test
    fun `player lookup hits the players endpoint and parses the body`() = runTest {
        server.handler = { respondJson(samplePlayer) }

        val player = server.api.player("Techno blade")

        assertEquals(samplePlayer, player)
        assertEquals("http://test/v1/players/Techno%20blade", server.requests.single().url.toString())
    }

    @Test
    fun `history sends the days parameter`() = runTest {
        server.handler = { respondJson(StatsHistory("abc123", "p1", emptyList())) }

        server.api.history("abc123", "p1", 7)

        val url = server.requests.single().url.toString()
        assertEquals("http://test/v1/players/abc123/profiles/p1/history?days=7", url)
    }

    @Test
    fun `errors carry the server's message`() = runTest {
        server.handler = { respondJson("""{"message":"Player not found"}""", HttpStatusCode.NotFound) }
        try {
            server.api.player("nobody")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(HttpStatusCode.NotFound, e.status)
            assertEquals("Player not found", e.message)
        }
    }

    @Test
    fun `register device PUTs the registration as JSON`() = runTest {
        server.handler = { respond("", HttpStatusCode.NoContent) }
        val registration = DeviceRegistration("token", subscribedEvents = setOf(EventType.DARK_AUCTION))

        server.api.registerDevice("install-1", registration)

        val request = server.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/v1/devices/install-1", request.url.encodedPath)
        val sent = SkydexJson.decodeFromString<DeviceRegistration>(request.body.toByteArray().decodeToString())
        assertEquals(registration, sent)
    }

    @Test
    fun `unregistering an unknown device is not an error`() = runTest {
        server.api.unregisterDevice("install-1")

        assertEquals(HttpMethod.Delete, server.requests.single().method)
    }
}
