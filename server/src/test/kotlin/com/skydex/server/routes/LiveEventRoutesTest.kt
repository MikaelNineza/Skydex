package com.skydex.server.routes

import com.skydex.server.hypixel.LiveEventSource
import com.skydex.server.hypixel.RateLimitedException
import com.skydex.server.hypixel.UpstreamException
import com.skydex.server.plugins.configureSerialization
import com.skydex.server.plugins.configureStatusPages
import com.skydex.shared.model.Candidate
import com.skydex.shared.model.Crop
import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.Mayor
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.Minister
import com.skydex.shared.model.Perk
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveEventRoutesTest {
    private val mayor = MayorStatus(
        mayor = Mayor("economist", "Diaz", listOf(Perk("Volume Trading", "Doubled."))),
        minister = Minister("mining", "Cole", Perk("Mining Fiesta", "Schedules 5 Mining Fiestas.", minister = true)),
        electionYear = 515,
        termStartsAt = 1,
        termEndsAt = 2,
        votingYear = 516,
        candidates = listOf(Candidate("pets", "Diana", listOf(Perk("Pet XP Buff", "Gain 35% more pet XP.")), 62133)),
    )
    private val contests = listOf(JacobContest(1_790_176_500_000L, listOf(Crop.CACTUS, Crop.CARROT, Crop.NETHER_WART)))

    private class FakeSource(var failure: Exception? = null, val mayor: MayorStatus, val contests: List<JacobContest>) :
        LiveEventSource {
        override suspend fun mayor(): MayorStatus = failure?.let { throw it } ?: mayor

        override suspend fun contests(): List<JacobContest> = failure?.let { throw it } ?: contests
    }

    private val source = FakeSource(mayor = mayor, contests = contests)

    private fun routesTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            routing { liveEventRoutes(source) }
        }
        block()
    }

    @Test
    fun mayorAndContests() = routesTest {
        val mayorResponse = client.get("/v1/mayor")
        assertEquals(HttpStatusCode.OK, mayorResponse.status)
        assertEquals(mayor, Json.decodeFromString<MayorStatus>(mayorResponse.bodyAsText()))

        val contestResponse = client.get("/v1/contests")
        assertEquals(HttpStatusCode.OK, contestResponse.status)
        assertEquals(contests, Json.decodeFromString<List<JacobContest>>(contestResponse.bodyAsText()))
    }

    @Test
    fun upstreamFailuresMapToGatewayErrors() = routesTest {
        source.failure = UpstreamException("Hypixel returned 500")
        assertEquals(HttpStatusCode.BadGateway, client.get("/v1/mayor").status)
        assertEquals(HttpStatusCode.BadGateway, client.get("/v1/contests").status)

        source.failure = UpstreamException("elitebot rate limit reached", RateLimitedException(30))
        val limited = client.get("/v1/contests")
        assertEquals(HttpStatusCode.ServiceUnavailable, limited.status)
        assertEquals("30", limited.headers[HttpHeaders.RetryAfter])
    }
}
