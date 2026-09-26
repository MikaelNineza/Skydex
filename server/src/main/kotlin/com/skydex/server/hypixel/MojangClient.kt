package com.skydex.server.hypixel

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.util.Base64

/** A Minecraft account: undashed UUID and current username. [properties] only come from the session server. */
@Serializable
data class MojangProfile(val id: String, val name: String, val properties: List<MojangProperty> = emptyList())

/** A session-server profile property; "textures" holds Base64 JSON with the skin URL. */
@Serializable
data class MojangProperty(val name: String, val value: String)

/**
 * The skin URL from the "textures" property, or null if there is none or it isn't on textures.minecraft.net (the
 * server downloads it, so only Mojang's texture host is allowed).
 */
internal fun MojangProfile.skinUrl(): String? = try {
    properties.firstOrNull { it.name == "textures" }?.let { property ->
        val json = upstreamJson.parseToJsonElement(String(Base64.getDecoder().decode(property.value))).jsonObject
        val skin = (json["textures"] as? JsonObject)?.get("SKIN") as? JsonObject
        val url = (skin?.get("url") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val uri = url?.let(::URI)
        if (uri?.host == "textures.minecraft.net" && uri.scheme in setOf("http", "https")) {
            // Mojang still hands out http:// texture URLs; the same path is served over https.
            "https://" + url.substringAfter("://")
        } else {
            null
        }
    }
} catch (e: Exception) {
    null
}

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
