package com.skydex.app.data.remote

import com.skydex.app.FakeServer
import com.skydex.app.respondJson
import com.skydex.app.samplePlayer
import com.skydex.shared.model.Candidate
import com.skydex.shared.model.Crop
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.Mayor
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.Minister
import com.skydex.shared.model.Perk
import com.skydex.shared.model.SkyblockProfile
import com.skydex.shared.model.StatsHistory
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `mayor and contests are fetched from the live endpoints`() = runTest {
        val mayor = MayorStatus(
            mayor = Mayor("economist", "Diaz", listOf(Perk("Volume Trading", "Doubled."))),
            minister = Minister("mining", "Cole", Perk("Mining Fiesta", "Five fiestas.", minister = true)),
            electionYear = 515,
            termStartsAt = 1,
            termEndsAt = 2,
            votingYear = 516,
            candidates = listOf(Candidate("pets", "Diana", emptyList(), 62133)),
        )
        server.handler = { request ->
            if (request.url.encodedPath == "/v1/mayor") {
                respondJson(mayor)
            } else {
                // A crop from a newer server is dropped instead of failing the list.
                respondJson("""[{"startsAt":5,"crops":["WHEAT","FUTURE_CROP","CARROT"]}]""")
            }
        }

        assertEquals(mayor, server.api.mayor())
        assertEquals(listOf(JacobContest(5, listOf(Crop.WHEAT, Crop.CARROT))), server.api.contests())
        assertEquals(listOf("/v1/mayor", "/v1/contests"), server.requests.map { it.url.encodedPath })
    }

    @Test
    fun `live endpoint errors carry the server's message`() = runTest {
        server.handler = { respondJson("""{"message":"Upstream service unavailable"}""", HttpStatusCode.BadGateway) }
        try {
            server.api.mayor()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(HttpStatusCode.BadGateway, e.status)
            assertEquals("Upstream service unavailable", e.message)
        }
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

    @Test
    fun `a cached profile from before fairy soul totals and skill averages still decodes`() {
        val cached = """{"profileId":"p1","cuteName":"Mango","uuid":"abc123","username":"Technoblade",""" +
            """"skyblockLevel":212.45,"purse":1.0,"bankBalance":null,"fairySouls":240,"skills":[],"slayers":[],""" +
            """"catacombs":null,"lastSave":null,"fetchedAt":1700000000000}"""

        val profile = SkydexJson.decodeFromString<SkyblockProfile>(cached)

        assertEquals(240, profile.fairySouls)
        assertEquals(289, profile.fairySoulsTotal)
        assertNull(profile.skillAverage)
        // Re-caching keeps the total, so the next read doesn't rely on the default.
        assertEquals(true, "\"fairySoulsTotal\":289" in SkydexJson.encodeToString(profile))
    }

    @Test
    fun `a mayor from a server without last election results still decodes`() = runTest {
        server.handler = {
            respondJson(
                """{"mayor":{"key":"economist","name":"Diaz","perks":[]},"electionYear":515,""" +
                    """"termStartsAt":1,"termEndsAt":2}""",
            )
        }

        val status = server.api.mayor()

        assertEquals(emptyList<Candidate>(), status.lastElectionCandidates)
        assertEquals(emptyList<Candidate>(), status.candidates)
        assertNull(status.votingYear)
    }
}
