package com.skydex.server.routes

import com.skydex.shared.calendar.SkyblockEvents
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.time.Duration.Companion.hours

/** `GET /v1/events?hours=24`: event occurrences running now or starting within the window, soonest first. */
fun Route.eventRoutes(now: () -> Long = System::currentTimeMillis) {
    get("/v1/events") {
        val hours = intParam(call.request.queryParameters["hours"], "hours", default = 24, range = 1..168)
        call.respond(SkyblockEvents.upcoming(now(), hours.hours.inWholeMilliseconds))
    }
}
