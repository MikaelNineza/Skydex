package com.skydex.server.hypixel

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val UUID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"

internal val profilesFixture: String =
    ClientsTest::class.java.getResource("/hypixel/profiles.json")!!.readText()

internal fun mockHttp(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    MockEngine(handler).let { it to HttpClient(it) }

private val json = headersOf(HttpHeaders.ContentType, "application/json")

class ClientsTest {
    @Test
    fun mojangResolvesUsernameAndUuid() = runBlocking<Unit> {
        val (engine, http) = mockHttp { request ->
            val url = request.url.toString()
            when {
                url == "https://api.mojang.com/users/profiles/minecraft/Tester" ->
                    respond("""{"id":"0F1E2D3C4B5A69788796A5B4C3D2E1F0","name":"Tester"}""", headers = json)
                url == "https://sessionserver.mojang.com/session/minecraft/profile/$UUID" ->
                    respond("""{"id":"$UUID","name":"Tester","properties":[]}""", headers = json)
                else -> respond("""{"errorMessage":"Couldn't find any profile"}""", HttpStatusCode.NotFound)
            }
        }
        val mojang = MojangClient(http)

        assertEquals(MojangProfile(UUID, "Tester"), mojang.byUsername("Tester"))
        assertEquals(MojangProfile(UUID, "Tester"), mojang.byUuid(UUID))
        assertFailsWith<PlayerNotFoundException> { mojang.byUsername("Nobody") }
        assertEquals(3, engine.requestHistory.size)
    }

    @Test
    fun mojangRateLimitAndErrors() = runBlocking<Unit> {
        var status = HttpStatusCode.TooManyRequests
        val (_, http) = mockHttp { respond("", status, headersOf(HttpHeaders.RetryAfter, "7")) }
        val mojang = MojangClient(http)

        val limited = assertFailsWith<UpstreamException> { mojang.byUsername("Tester") }
        assertEquals(7, assertIs<RateLimitedException>(limited.cause).retryAfterSeconds)

        status = HttpStatusCode.NoContent
        assertFailsWith<PlayerNotFoundException> { mojang.byUuid(UUID) }

        status = HttpStatusCode.InternalServerError
        assertFailsWith<UpstreamException> { mojang.byUsername("Tester") }
    }

    @Test
    fun hypixelSendsKeyAndParsesProfiles() = runBlocking<Unit> {
        val (engine, http) = mockHttp { respond(profilesFixture, headers = json) }

        val profiles = HypixelClient(http, "secret-key").skyblockProfiles(UUID)

        assertEquals(2, profiles.size)
        val request = engine.requestHistory.single()
        assertEquals("https://api.hypixel.net/v2/skyblock/profiles?uuid=$UUID", request.url.toString())
        assertEquals("secret-key", request.headers["API-Key"])
    }

    @Test
    fun hypixelPlayerWithoutSkyblockHasNoProfiles() = runBlocking<Unit> {
        val (_, http) = mockHttp { respond("""{"success":true,"profiles":null}""", headers = json) }
        assertTrue(HypixelClient(http, "key").skyblockProfiles(UUID).isEmpty())
    }

    @Test
    fun hypixelErrorsBecomeUpstreamExceptions() = runBlocking<Unit> {
        var status = HttpStatusCode.Forbidden
        val (_, http) = mockHttp { respond("""{"success":false,"cause":"Invalid API key"}""", status, json) }
        val client = HypixelClient(http, "key")

        assertFailsWith<UpstreamException> { client.skyblockProfiles(UUID) }
        status = HttpStatusCode.BadGateway
        assertFailsWith<UpstreamException> { client.skyblockProfiles(UUID) }
    }

    @Test
    fun hypixelRateLimitBlocksFurtherCallsUntilReset() = runBlocking<Unit> {
        var now = 1_000_000L
        val (engine, http) = mockHttp {
            respond("""{"success":false,"throttle":true}""", HttpStatusCode.TooManyRequests,
                headersOf("RateLimit-Reset", "30"))
        }
        val client = HypixelClient(http, "key", clock = { now })

        val first = assertFailsWith<UpstreamException> { client.skyblockProfiles(UUID) }
        assertEquals(30, assertIs<RateLimitedException>(first.cause).retryAfterSeconds)

        now += 10_000
        val second = assertFailsWith<UpstreamException> { client.skyblockProfiles(UUID) }
        assertEquals(20, assertIs<RateLimitedException>(second.cause).retryAfterSeconds)
        assertEquals(1, engine.requestHistory.size)

        now += 20_000
        assertFailsWith<UpstreamException> { client.skyblockProfiles(UUID) }
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun hypixelWithoutKeyFailsWithoutCalling() = runBlocking<Unit> {
        val (engine, http) = mockHttp { respond(profilesFixture, headers = json) }
        assertFailsWith<UpstreamException> { HypixelClient(http, "").skyblockProfiles(UUID) }
        assertTrue(engine.requestHistory.isEmpty())
    }
}
