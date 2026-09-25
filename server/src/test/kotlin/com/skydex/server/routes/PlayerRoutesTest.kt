package com.skydex.server.routes

import com.skydex.server.hypixel.PlayerNotFoundException
import com.skydex.server.hypixel.ProfileSource
import com.skydex.server.hypixel.RateLimitedException
import com.skydex.server.hypixel.UpstreamException
import com.skydex.server.plugins.configureSerialization
import com.skydex.server.plugins.configureStatusPages
import com.skydex.shared.model.ApiError
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.ProfileSummary
import com.skydex.shared.model.SkyblockProfile
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private const val UUID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"
private const val PROFILE = "a1b2c3d4e5f64a1b8c9d0e1f2a3b4c5d"

class PlayerRoutesTest {
    /** Returns canned data for "Tester", and fails in the way the name asks for otherwise. */
    private val fake = object : ProfileSource {
        val requested = mutableListOf<String>()

        override suspend fun playerProfiles(nameOrUuid: String): PlayerProfiles {
            requested += nameOrUuid
            return when (nameOrUuid) {
                "Tester", UUID -> PlayerProfiles(UUID, "Tester", listOf(ProfileSummary(PROFILE, "Mango", null, true)))
                "Throttled" -> throw UpstreamException("Hypixel rate limit reached", RateLimitedException(42))
                "Down" -> throw UpstreamException("Hypixel returned 500")
                "Boom" -> throw IllegalStateException("secret internal detail")
                else -> throw PlayerNotFoundException("Unknown player $nameOrUuid")
            }
        }

        override suspend fun profile(uuid: String, profileId: String): SkyblockProfile {
            if (uuid != UUID || profileId != PROFILE) throw PlayerNotFoundException("No such profile")
            return SkyblockProfile(
                profileId = PROFILE, cuteName = "Mango", uuid = UUID, username = "Tester", skyblockLevel = 212.45,
                purse = 1.0, fairySouls = 238, skills = emptyList(), slayers = emptyList(), fetchedAt = 1L,
            )
        }
    }

    private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            configurePlayerRoutes(fake)
        }
        block()
    }

    private suspend fun HttpResponse.error(): ApiError = Json.decodeFromString(bodyAsText())

    @Test
    fun playerByUsernameOrUuid() = routesTest {
        val byName = client.get("/v1/players/Tester")
        assertEquals(HttpStatusCode.OK, byName.status)
        assertEquals(UUID, Json.decodeFromString<PlayerProfiles>(byName.bodyAsText()).uuid)

        val dashed = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0".uppercase()
        assertEquals(HttpStatusCode.OK, client.get("/v1/players/$dashed").status)
        assertEquals(listOf("Tester", UUID), fake.requested)
    }

    @Test
    fun profileById() = routesTest {
        val response = client.get("/v1/players/$UUID/profiles/a1b2c3d4-e5f6-4a1b-8c9d-0e1f2a3b4c5d")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(212.45, Json.decodeFromString<SkyblockProfile>(response.bodyAsText()).skyblockLevel)
    }

    @Test
    fun badInputIs400() = routesTest {
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/players/not%20a%20name!").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/players/ThisNameIsWayTooLong").status)
        val badProfile = client.get("/v1/players/$UUID/profiles/nope")
        assertEquals(HttpStatusCode.BadRequest, badProfile.status)
        assertEquals("Invalid profile id: nope", badProfile.error().message)
        assertEquals(emptyList(), fake.requested)
    }

    @Test
    fun unknownPlayerOrProfileIs404() = routesTest {
        val response = client.get("/v1/players/Nobody")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Unknown player Nobody", response.error().message)

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/players/$UUID/profiles/${"0".repeat(32)}").status)
    }

    @Test
    fun upstreamFailures() = routesTest {
        val throttled = client.get("/v1/players/Throttled")
        assertEquals(HttpStatusCode.ServiceUnavailable, throttled.status)
        assertEquals("42", throttled.headers[HttpHeaders.RetryAfter])
        throttled.error()

        val down = client.get("/v1/players/Down")
        assertEquals(HttpStatusCode.BadGateway, down.status)
        down.error()
    }

    @Test
    fun unexpectedErrorsDoNotLeakDetails() = routesTest {
        val response = client.get("/v1/players/Boom")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("Internal server error", response.error().message)
    }

    @Test
    fun unknownRouteIsApiError() = routesTest {
        val response = client.get("/v1/nothing-here")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Not Found", response.error().message)
    }
}
