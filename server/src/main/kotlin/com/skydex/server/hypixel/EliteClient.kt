package com.skydex.server.hypixel

import com.skydex.shared.model.Crop
import com.skydex.shared.model.JacobContest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Reads Jacob's contest crops from api.elitebot.dev, which publishes a whole Skyblock year's contests at once. */
class EliteClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://api.elitebot.dev",
) {
    /** Keys are contest start times in Unix seconds; values are crop display names. */
    @Serializable
    private class RawContests(val contests: Map<String, List<String>> = emptyMap())

    /** `GET /contests/at/now`: the current Skyblock year's contests, soonest first. Unknown crops are left out. */
    suspend fun contestsNow(): List<JacobContest> {
        val response = upstreamCall("elitebot") { http.get("$baseUrl/contests/at/now") }
        when {
            response.status == HttpStatusCode.TooManyRequests ->
                throw rateLimited("elitebot", retryAfterSeconds(response))
            !response.status.isSuccess() -> throw UpstreamException("elitebot returned ${response.status.value}")
        }

        return try {
            upstreamJson.decodeFromString<RawContests>(response.bodyAsText()).contests
                .map { (seconds, names) ->
                    JacobContest(seconds.toLong() * 1000, names.mapNotNull { Crop.fromDisplayName(it) })
                }
                .sortedBy { it.startsAt }
        } catch (e: IllegalArgumentException) { // includes SerializationException and NumberFormatException
            throw UpstreamException("Unexpected elitebot response", e)
        }
    }
}
