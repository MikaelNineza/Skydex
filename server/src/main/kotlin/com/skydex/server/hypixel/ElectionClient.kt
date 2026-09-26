package com.skydex.server.hypixel

import com.skydex.shared.calendar.SkyblockDate
import com.skydex.shared.calendar.termBounds
import com.skydex.shared.model.Candidate
import com.skydex.shared.model.Mayor
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.Minister
import com.skydex.shared.model.Perk
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Minecraft § formatting codes, e.g. "§6". */
private val FORMATTING_CODE = Regex("§.")

/** How many Skyblock years the election year may be from the current one before we distrust it. */
private const val MAX_YEAR_SKEW = 2

/** Elections have five candidates; cap what we pass on in case the upstream list balloons. */
private const val MAX_CANDIDATES = 10

/** Reads the current mayor and election from Hypixel's public election resource. Needs no API key. */
class ElectionClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://api.hypixel.net",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Serializable
    private class RawPerk(val name: String, val description: String, val minister: Boolean = false)

    @Serializable
    private class RawCandidate(val key: String, val name: String, val perks: List<RawPerk>, val votes: Long = 0)

    @Serializable
    private class RawMinister(val key: String, val name: String, val perk: RawPerk)

    @Serializable
    private class RawElection(val year: Int, val candidates: List<RawCandidate> = emptyList())

    @Serializable
    private class RawMayor(
        val key: String,
        val name: String,
        val perks: List<RawPerk>,
        val minister: RawMinister? = null,
        val election: RawElection,
    )

    /** `current` is only present while the booth is open. */
    @Serializable
    private class RawResponse(val mayor: RawMayor, val current: RawElection? = null)

    /** `GET /v2/resources/skyblock/election`: the mayor in office and, while voting is open, the running election. */
    suspend fun election(): MayorStatus {
        val response = upstreamCall("Hypixel") { http.get("$baseUrl/v2/resources/skyblock/election") }
        when {
            response.status == HttpStatusCode.TooManyRequests ->
                throw rateLimited("Hypixel", retryAfterSeconds(response))
            !response.status.isSuccess() -> throw UpstreamException("Hypixel returned ${response.status.value}")
        }

        val raw = try {
            upstreamJson.decodeFromString<RawResponse>(response.bodyAsText())
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw UpstreamException("Unexpected Hypixel election response", e)
        }
        val mayor = raw.mayor
        // A bogus year would make the calendar project gated events from a far-off term; refuse it.
        val currentYear = SkyblockDate.fromMillis(clock()).year
        if (mayor.election.year !in currentYear - MAX_YEAR_SKEW..currentYear + MAX_YEAR_SKEW) {
            throw UpstreamException("Hypixel election year ${mayor.election.year} is not near $currentYear")
        }
        val (termStartsAt, termEndsAt) = termBounds(mayor.election.year)
        return MayorStatus(
            mayor = Mayor(mayor.key, mayor.name.stripFormatting(), mayor.perks.map { it.toPerk() }),
            minister = mayor.minister?.let { Minister(it.key, it.name.stripFormatting(), it.perk.toPerk()) },
            electionYear = mayor.election.year,
            termStartsAt = termStartsAt,
            termEndsAt = termEndsAt,
            votingYear = raw.current?.year,
            candidates = raw.current?.candidates.orEmpty().take(MAX_CANDIDATES).map { it.toCandidate() },
            // The app only shows past results' names and votes, so their perks aren't worth sending.
            lastElectionCandidates = mayor.election.candidates.take(MAX_CANDIDATES)
                .map { it.toCandidate().copy(perks = emptyList()) },
        )
    }

    private fun String.stripFormatting() = replace(FORMATTING_CODE, "")

    private fun RawCandidate.toCandidate() =
        Candidate(key, name.stripFormatting(), perks.map { it.toPerk() }, votes.coerceAtLeast(0))

    private fun RawPerk.toPerk() = Perk(
        name = name.stripFormatting(),
        description = description.stripFormatting(),
        minister = minister,
    )
}
