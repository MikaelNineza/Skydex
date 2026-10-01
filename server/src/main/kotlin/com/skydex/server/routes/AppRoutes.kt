package com.skydex.server.routes

import com.skydex.server.releases.AppReleaseSource
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /v1/app/latest` -> [com.skydex.shared.model.AppRelease] (200), or 204 when nothing is published.
 * Upstream failures map to 502/503.
 */
fun Route.appRoutes(source: AppReleaseSource) {
    get("/v1/app/latest") {
        val release = source.latest()
        if (release == null) call.respond(HttpStatusCode.NoContent) else call.respond(release)
    }
}
