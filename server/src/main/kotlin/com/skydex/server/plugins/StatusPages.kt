package com.skydex.server.plugins

import com.skydex.server.hypixel.PlayerNotFoundException
import com.skydex.server.hypixel.RateLimitedException
import com.skydex.server.hypixel.UpstreamException
import com.skydex.shared.model.ApiError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond

/** Makes every error response an [ApiError] body. Unexpected exceptions become a 500 without internals. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError(e.message ?: "Bad request"))
        }
        exception<NotFoundException> { call, e ->
            call.respond(HttpStatusCode.NotFound, ApiError(e.message ?: "Not found"))
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ApiError("Unsupported media type"))
        }
        exception<PlayerNotFoundException> { call, e ->
            call.respond(HttpStatusCode.NotFound, ApiError(e.message ?: "Player not found"))
        }
        exception<UpstreamException> { call, e ->
            // Walk the chain: coroutine stack-trace recovery may wrap the original exception in a copy.
            val rateLimit = generateSequence(e.cause) { it.cause }
                .filterIsInstance<RateLimitedException>()
                .firstOrNull()
            call.application.log.warn("Upstream failure: ${e.message}", e.cause)
            if (rateLimit != null) {
                call.response.header(HttpHeaders.RetryAfter, rateLimit.retryAfterSeconds)
                call.respond(HttpStatusCode.ServiceUnavailable, ApiError("Rate limited by Hypixel or Mojang"))
            } else {
                call.respond(HttpStatusCode.BadGateway, ApiError("Hypixel or Mojang is unavailable"))
            }
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled error on ${call.request.local.uri}", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Internal server error"))
        }
        // Error statuses responded without a body (unknown routes, 405s, auth challenges) also get an ApiError.
        status(*HttpStatusCode.allStatusCodes.filter { it.value >= 400 }.toTypedArray()) { status ->
            if (content.contentType == null) call.respond(status, ApiError(status.description))
        }
    }
}
