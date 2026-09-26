package com.skydex.server.hypixel

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Calls the Hypixel API with the server's key. The key is only ever sent in the `API-Key` header, never logged.
 *
 * After a 429 it stops calling Hypixel until the reset time the API gave, so a burst of requests can't get the key
 * locked out.
 */
class HypixelClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.hypixel.net",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var blockedUntil = 0L

    /** True while a 429 from Hypixel is in effect; calls fail without reaching Hypixel until it passes. */
    val isRateLimited: Boolean get() = blockedUntil > clock()

    /**
     * Raw `profiles` array from `GET /v2/skyblock/profiles` for an undashed [uuid]. Empty if the player has never
     * played Skyblock.
     */
    suspend fun skyblockProfiles(uuid: String): List<JsonObject> {
        val body = get("/v2/skyblock/profiles", uuid)
        return try {
            // "profiles" is null for players who have never joined Skyblock.
            (body["profiles"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
        } catch (e: IllegalArgumentException) {
            throw UpstreamException("Unexpected Hypixel response", e)
        }
    }

    /** Raw `player` object from `GET /v2/player` for an undashed [uuid], or null if they never joined Hypixel. */
    suspend fun player(uuid: String): JsonObject? = get("/v2/player", uuid)["player"] as? JsonObject

    /** Calls [path] for [uuid] and returns the JSON body; throws [UpstreamException] on any failure. */
    private suspend fun get(path: String, uuid: String): JsonObject {
        if (apiKey.isBlank()) throw UpstreamException("Hypixel API key is not configured")
        val waitMillis = blockedUntil - clock()
        if (waitMillis > 0) throw rateLimited("Hypixel", (waitMillis + 999) / 1000)

        val response = upstreamCall("Hypixel") {
            http.get("$baseUrl$path") {
                parameter("uuid", uuid)
                header("API-Key", apiKey)
            }
        }
        when {
            response.status == HttpStatusCode.TooManyRequests -> {
                val seconds = retryAfterSeconds(response)
                blockedUntil = clock() + seconds * 1000
                throw rateLimited("Hypixel", seconds)
            }
            response.status == HttpStatusCode.Forbidden -> throw UpstreamException("Hypixel rejected the API key")
            !response.status.isSuccess() -> throw UpstreamException("Hypixel returned ${response.status.value}")
        }

        return try {
            upstreamJson.parseToJsonElement(response.bodyAsText()).jsonObject
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw UpstreamException("Unexpected Hypixel response", e)
        }
    }
}
