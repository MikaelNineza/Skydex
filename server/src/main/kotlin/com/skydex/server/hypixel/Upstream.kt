package com.skydex.server.hypixel

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import kotlinx.serialization.json.Json
import java.io.IOException

/** Attached as the cause of an [UpstreamException] when Hypixel or Mojang rate-limited us. */
class RateLimitedException(val retryAfterSeconds: Long) :
    Exception("Rate limited, retry in $retryAfterSeconds s")

internal val upstreamJson = Json { ignoreUnknownKeys = true }

private val UUID_REGEX = Regex("[0-9a-fA-F]{32}")
private val USERNAME_REGEX = Regex("[A-Za-z0-9_]{1,16}")

/** Lowercase undashed form of a dashed or undashed UUID, or null if [value] isn't one. */
fun undashedUuidOrNull(value: String): String? =
    value.replace("-", "").takeIf { it.length == 32 && UUID_REGEX.matches(it) }?.lowercase()

/** True if [value] could be a Minecraft username. */
fun isValidUsername(value: String): Boolean = USERNAME_REGEX.matches(value)

/** Runs [request], turning network failures into [UpstreamException]. */
internal suspend fun upstreamCall(service: String, request: suspend () -> HttpResponse): HttpResponse =
    try {
        request()
    } catch (e: IOException) {
        throw UpstreamException("$service is unreachable", e)
    }

/** Seconds to wait from `RateLimit-Reset` or `Retry-After`, defaulting to a minute. */
internal fun retryAfterSeconds(response: HttpResponse): Long =
    (response.headers["RateLimit-Reset"] ?: response.headers[HttpHeaders.RetryAfter])
        ?.toLongOrNull()
        ?.coerceAtLeast(1)
        ?: 60

internal fun rateLimited(service: String, seconds: Long) =
    UpstreamException("$service rate limit reached", RateLimitedException(seconds))

/**
 * Builds the production [ProfileSource] from `hypixel.apiKey` in application.conf. An empty key is allowed so the
 * server still starts; player requests then fail with 502.
 */
fun Application.hypixelProfileSource(): HypixelProfileSource {
    val apiKey = environment.config.propertyOrNull("hypixel.apiKey")?.getString().orEmpty()
    if (apiKey.isBlank()) log.warn("hypixel.apiKey is not set; player lookups will fail")
    val http = HttpClient(CIO) {
        engine { requestTimeout = 15_000 }
    }
    monitor.subscribe(ApplicationStopped) { http.close() }
    return HypixelProfileSource(MojangClient(http), HypixelClient(http, apiKey))
}
