package com.skydex.server.hypixel

import com.skydex.shared.calendar.SkyblockDate
import com.skydex.shared.calendar.activePerks
import com.skydex.shared.calendar.termBounds
import com.skydex.shared.model.Candidate
import com.skydex.shared.model.Crop
import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.Perk
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val electionFixture: String =
    LiveEventSourceTest::class.java.getResource("/hypixel/election.json")!!.readText()
private val eliteFixture: String =
    LiveEventSourceTest::class.java.getResource("/hypixel/elite.json")!!.readText()

private val json = headersOf(HttpHeaders.ContentType, "application/json")
private const val MINUTE = 60_000L

/** Year 516, Late Summer: the sample's election (515) is current. */
private val YEAR_516 = SkyblockDate(516, 6, 1).toMillis()

class LiveEventSourceTest {
    @Test
    fun electionParsesMayorMinisterAndCandidates() = runBlocking<Unit> {
        val (engine, http) = mockHttp { respond(electionFixture, headers = json) }

        val status = ElectionClient(http, clock = { YEAR_516 }).election()

        val request = engine.requestHistory.single()
        assertEquals("https://api.hypixel.net/v2/resources/skyblock/election", request.url.toString())
        assertNull(request.headers["API-Key"], "the election resource needs no key")
        assertEquals("economist", status.mayor.key)
        assertEquals("Diaz", status.mayor.name)
        assertEquals(listOf("Volume Trading", "Long Term Investment"), status.mayor.perks.map { it.name })
        assertEquals(
            "The available item quantity per Shen's Auction has been doubled, and two additional Shen's Special " +
                "auctions will be available if Diaz is Mayor.",
            status.mayor.perks[0].description,
        )
        assertEquals("Cole", status.minister?.name)
        assertEquals("Mining Fiesta", status.minister?.perk?.name)
        assertTrue(status.minister!!.perk.minister)
        assertEquals(515, status.electionYear)
        assertEquals(termBounds(515), status.termStartsAt to status.termEndsAt)
        assertEquals(516, status.votingYear)
        assertEquals(listOf("Diana", "Finnegan", "Marina", "Foxy", "Cole"), status.candidates.map { it.name })
        assertEquals(62133L, status.candidates[0].votes)
        // The last election (the one Diaz won) keeps Hypixel's order and final votes, without perks.
        assertEquals(
            listOf("Cole", "Diana", "Paul", "Diaz", "Foxy"),
            status.lastElectionCandidates.map { it.name },
        )
        assertEquals(529099L, status.lastElectionCandidates.single { it.key == "economist" }.votes)
        assertTrue(status.lastElectionCandidates.all { it.perks.isEmpty() })
        val allText = (status.mayor.perks + status.minister!!.perk + status.candidates.flatMap { it.perks })
            .flatMap { listOf(it.name, it.description) } +
            (status.candidates + status.lastElectionCandidates).map { it.name }
        assertTrue(allText.none { '§' in it }, "formatting codes left: ${allText.filter { '§' in it }}")
        // Cole's minister perk makes Mining Fiestas active for the term.
        assertTrue(status.activePerks().miningFiesta)
    }

    @Test
    fun electionWithoutOpenBoothHasNoCandidatesAndStripsNames() = runBlocking<Unit> {
        val body = """{"success":true,"mayor":{"key":"fishing","name":"§bMarina","perks":[""" +
            """{"name":"§aFishing Festival","description":"Start a §6Fishing Festival§7!","minister":false}],""" +
            """"election":{"year":515,"candidates":[{"key":"fishing","name":"§bMarina","perks":[""" +
            """{"name":"§aFishing Festival","description":"Start one.","minister":false}],"votes":1}]}}}"""
        val (_, http) = mockHttp { respond(body, headers = json) }

        val status = ElectionClient(http, clock = { YEAR_516 }).election()

        assertEquals("Marina", status.mayor.name)
        assertEquals(listOf(Perk("Fishing Festival", "Start a Fishing Festival!")), status.mayor.perks)
        assertNull(status.minister)
        assertNull(status.votingYear)
        assertEquals(emptyList(), status.candidates, "past election's candidates must not be shown")
        assertEquals(listOf(Candidate("fishing", "Marina", emptyList(), 1)), status.lastElectionCandidates)
        assertTrue(status.activePerks().fishingFestival)
    }

    @Test
    fun electionCandidateListsAreCappedAndVotesClamped() = runBlocking<Unit> {
        fun candidates(n: Int, votes: (Int) -> Long) = (1..n).joinToString(",") {
            """{"key":"k$it","name":"C$it","perks":[],"votes":${votes(it)}}"""
        }
        val body = """{"success":true,"mayor":{"key":"fishing","name":"Marina","perks":[],""" +
            """"election":{"year":515,"candidates":[${candidates(12) { if (it == 1) -5 else it.toLong() }}]}},""" +
            """"current":{"year":516,"candidates":[${candidates(15) { -it.toLong() }}]}}"""
        val (_, http) = mockHttp { respond(body, headers = json) }

        val status = ElectionClient(http, clock = { YEAR_516 }).election()

        assertEquals((1..10).map { "C$it" }, status.candidates.map { it.name })
        assertTrue(status.candidates.all { it.votes == 0L }, "negative votes: ${status.candidates.map { it.votes }}")
        assertEquals((1..10).map { "C$it" }, status.lastElectionCandidates.map { it.name })
        assertEquals(listOf(0L) + (2..10).map { it.toLong() }, status.lastElectionCandidates.map { it.votes })
    }

    @Test
    fun electionRejectsAbsurdYearsAndErrors() = runBlocking<Unit> {
        var body = electionFixture.replace("\"year\":515", "\"year\":9999")
        var status = HttpStatusCode.OK
        var headers = json
        val (_, http) = mockHttp { respond(body, status, headers) }
        val client = ElectionClient(http, clock = { YEAR_516 })

        assertFailsWith<UpstreamException> { client.election() }
        body = electionFixture.replace("\"year\":515", "\"year\":513")
        assertFailsWith<UpstreamException> { client.election() }
        body = electionFixture.replace("\"year\":515", "\"year\":514")
        assertEquals(514, client.election().electionYear)

        body = "not json"
        assertFailsWith<UpstreamException> { client.election() }

        status = HttpStatusCode.InternalServerError
        assertFailsWith<UpstreamException> { client.election() }

        status = HttpStatusCode.TooManyRequests
        headers = headersOf(HttpHeaders.RetryAfter, "9")
        val limited = assertFailsWith<UpstreamException> { client.election() }
        assertEquals(9, assertIs<RateLimitedException>(limited.cause).retryAfterSeconds)
    }

    @Test
    fun eliteContestsInMillisSortedWithUnknownCropsDropped() = runBlocking<Unit> {
        val (engine, http) = mockHttp { respond(eliteFixture, headers = json) }

        val contests = EliteClient(http).contestsNow()

        assertEquals("https://api.elitebot.dev/contests/at/now", engine.requestHistory.single().url.toString())
        assertEquals(
            listOf(
                JacobContest(1_790_172_900_000L, listOf(Crop.MUSHROOM, Crop.PUMPKIN, Crop.SUGAR_CANE)),
                JacobContest(1_790_176_500_000L, listOf(Crop.CACTUS, Crop.CARROT, Crop.NETHER_WART)),
                JacobContest(1_790_180_100_000L, listOf(Crop.COCOA_BEANS, Crop.SUNFLOWER, Crop.MOONFLOWER)),
                JacobContest(1_790_183_700_000L, listOf(Crop.WHEAT, Crop.WILD_ROSE)),
            ),
            contests,
        )
    }

    @Test
    fun eliteErrors() = runBlocking<Unit> {
        var body = """{"contests":{"soon":["Wheat"]}}"""
        var status = HttpStatusCode.OK
        val (_, http) = mockHttp { respond(body, status, json) }
        val client = EliteClient(http)

        assertFailsWith<UpstreamException> { client.contestsNow() }
        body = """{"year":516}"""
        assertEquals(emptyList(), client.contestsNow())
        status = HttpStatusCode.BadGateway
        assertFailsWith<UpstreamException> { client.contestsNow() }
    }

    @Test
    fun concurrentCallersShareOneRequest() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val (engine, http) = mockHttp {
            gate.await()
            respond(electionFixture, headers = json)
        }
        val source = CachedLiveEventSource(ElectionClient(http, clock = { YEAR_516 }), EliteClient(http), { YEAR_516 })

        val callers = List(5) { async { source.mayor() } }
        repeat(5) { yield() }
        gate.complete(Unit)
        val results = callers.awaitAll()

        assertEquals(1, engine.requestHistory.size)
        results.forEach { assertSame(results[0], it) }
        // Still fresh: no new request.
        source.mayor()
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun staleValueIsServedWhenARefreshFailsAndBackoffIsHonoured() = runBlocking<Unit> {
        var now = YEAR_516
        var status = HttpStatusCode.OK
        val (engine, http) = mockHttp { respond(electionFixture, status, json) }
        val source = CachedLiveEventSource(
            ElectionClient(http, clock = { now }),
            EliteClient(http),
            { now },
            mayorTtlMillis = 5 * MINUTE,
            failureBackoffMillis = MINUTE,
        )
        val good = source.mayor()

        now += 5 * MINUTE
        status = HttpStatusCode.InternalServerError
        assertEquals(good, source.mayor())
        assertEquals(2, engine.requestHistory.size)

        // Within the backoff the upstream isn't called again, even though the value is stale.
        now += MINUTE - 1
        assertEquals(good, source.mayor())
        assertEquals(2, engine.requestHistory.size)

        now += 1
        status = HttpStatusCode.OK
        assertEquals(good, source.mayor())
        assertEquals(3, engine.requestHistory.size)
    }

    @Test
    fun failureWithoutCachedValueThrowsAndBacksOffByRetryAfter() = runBlocking<Unit> {
        var now = YEAR_516
        val (engine, http) = mockHttp {
            respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "120"))
        }
        val source = CachedLiveEventSource(
            ElectionClient(http, clock = { now }),
            EliteClient(http),
            { now },
            failureBackoffMillis = MINUTE,
        )

        val first = assertFailsWith<UpstreamException> { source.mayor() }
        now += 2 * MINUTE - 1
        val second = assertFailsWith<UpstreamException> { source.mayor() }
        assertSame(first, second)
        assertEquals(1, engine.requestHistory.size)

        now += 1
        assertFailsWith<UpstreamException> { source.mayor() }
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun contestsLeaveOutEndedOnesAndAreCached() = runBlocking<Unit> {
        // 5 minutes into the second contest: the first has ended.
        var now = 1_790_176_500_000L + 5 * MINUTE
        val (engine, http) = mockHttp { respond(eliteFixture, headers = json) }
        val source = CachedLiveEventSource(ElectionClient(http), EliteClient(http), { now })

        assertEquals(
            listOf(1_790_176_500_000L, 1_790_180_100_000L, 1_790_183_700_000L),
            source.contests().map { it.startsAt },
        )
        now += 20 * MINUTE
        assertEquals(listOf(1_790_180_100_000L, 1_790_183_700_000L), source.contests().map { it.startsAt })
        assertEquals(1, engine.requestHistory.size)
        assertFalse(source.contests().any { it.startsAt + 20 * MINUTE <= now })
    }
}
