package com.skydex.server.hypixel

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** A Minecraft account: undashed UUID and current username. */
@Serializable
data class MojangProfile(val id: String, val name: String)

/** Resolves Minecraft usernames and UUIDs through Mojang's public API. */
class MojangClient(
    private val http: HttpClient,
    private val apiBase: String = "https://api.mojang.com",
    private val sessionBase: String = "https://sessionserver.mojang.com",
) {
    /** Looks up a username. Throws [PlayerNotFoundException] if no account has it. */
    suspend fun byUsername(username: String): MojangProfile =
        fetch("$apiBase/users/profiles/minecraft/$username", "player $username")

    /** Looks up an undashed UUID to get the current username. Throws [PlayerNotFoundException] if unknown. */
    suspend fun byUuid(uuid: String): MojangProfile =
        fetch("$sessionBase/session/minecraft/profile/$uuid", "player $uuid")

    private suspend fun fetch(url: String, what: String): MojangProfile {
        val response = upstreamCall("Mojang") { http.get(url) }
        return when {
            // Mojang answers unknown names with 404 (older responses used 204) and malformed ones with 400.
            response.status == HttpStatusCode.NoContent ||
                response.status == HttpStatusCode.NotFound ||
                response.status == HttpStatusCode.BadRequest -> throw PlayerNotFoundException("Unknown $what")
            response.status == HttpStatusCode.TooManyRequests ->
                throw rateLimited("Mojang", retryAfterSeconds(response))
            !response.status.isSuccess() -> throw UpstreamException("Mojang returned ${response.status.value}")
            else -> parse(response)
        }
    }

    private suspend fun parse(response: HttpResponse): MojangProfile =
        try {
            upstreamJson.decodeFromString<MojangProfile>(response.bodyAsText()).let { it.copy(id = it.id.lowercase()) }
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw UpstreamException("Unexpected Mojang response", e)
        }
}
