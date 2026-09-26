package com.skydex.server.routes

import com.skydex.server.hypixel.LiveEventSource
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

/**
 * `GET /v1/mayor` -> [com.skydex.shared.model.MayorStatus] and `GET /v1/contests` -> upcoming
 * [com.skydex.shared.model.JacobContest]s, both from [source]. Upstream failures map to 502/503.
 */
fun Route.liveEventRoutes(source: LiveEventSource) {
    get("/v1/mayor") {
        call.respond(source.mayor())
    }
    get("/v1/contests") {
        call.respond(source.contests())
    }
}
