package com.skydex.server.routes

import com.skydex.server.db.SnapshotRepository
import com.skydex.shared.model.StatsHistory
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.time.Duration.Companion.days

private val UUID_REGEX = Regex("[0-9a-f]{32}")
private val PROFILE_ID_REGEX = Regex("[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/** Throws [BadRequestException] unless [uuid] is an undashed lowercase Minecraft UUID. */
internal fun requireUuid(uuid: String?): String =
    uuid?.takeIf { it.matches(UUID_REGEX) } ?: throw BadRequestException("uuid must be 32 lowercase hex characters")

/** Throws [BadRequestException] unless [profileId] looks like a Hypixel profile id (a UUID, dashed or not). */
internal fun requireProfileId(profileId: String?): String =
    profileId?.takeIf { it.matches(PROFILE_ID_REGEX) } ?: throw BadRequestException("profileId must be a UUID")

/** Parses an optional integer query parameter, defaulting when absent and clamping to [range]. */
internal fun intParam(value: String?, name: String, default: Int, range: IntRange): Int {
    if (value == null) return default
    val parsed = value.toIntOrNull() ?: throw BadRequestException("$name must be an integer")
    return parsed.coerceIn(range)
}

/** `GET /v1/players/{uuid}/profiles/{profileId}/history?days=30`. */
fun Route.historyRoutes(snapshots: SnapshotRepository, now: () -> Long = System::currentTimeMillis) {
    get("/v1/players/{uuid}/profiles/{profileId}/history") {
        val uuid = requireUuid(call.parameters["uuid"])
        val profileId = requireProfileId(call.parameters["profileId"])
        val days = intParam(call.request.queryParameters["days"], "days", default = 30, range = 1..365)
        val points = snapshots.history(uuid, profileId, sinceMillis = now() - days.days.inWholeMilliseconds)
        call.respond(StatsHistory(uuid, profileId, points))
    }
}
