package com.skydex.server.routes

import com.skydex.server.db.DeviceRepository
import com.skydex.shared.model.DeviceRegistration
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.put

private val INSTALLATION_ID_REGEX = Regex("[A-Za-z0-9-]{1,64}")

/** Longest lead time a device may ask for: one day. */
private const val MAX_LEAD_MINUTES = 24 * 60

private fun requireInstallationId(id: String?): String =
    id?.takeIf { it.matches(INSTALLATION_ID_REGEX) }
        ?: throw BadRequestException("installationId must be 1-64 letters, digits or dashes")

private fun validate(registration: DeviceRegistration) {
    with(registration) {
        // Blank means no push token yet: the device only wants its profile tracked.
        if (fcmToken.length > 4096) throw BadRequestException("fcmToken is invalid")
        if ((trackedUuid == null) != (trackedProfileId == null)) {
            throw BadRequestException("trackedUuid and trackedProfileId must be set together")
        }
        trackedUuid?.let { requireUuid(it) }
        trackedProfileId?.let { requireProfileId(it) }
        if (leadMinutes !in 0..MAX_LEAD_MINUTES) {
            throw BadRequestException("leadMinutes must be between 0 and $MAX_LEAD_MINUTES")
        }
    }
}

/** `PUT` and `DELETE /v1/devices/{installationId}`. */
fun Route.deviceRoutes(devices: DeviceRepository, now: () -> Long = System::currentTimeMillis) {
    put("/v1/devices/{installationId}") {
        val installationId = requireInstallationId(call.parameters["installationId"])
        val registration = call.receive<DeviceRegistration>()
        validate(registration)
        devices.upsert(installationId, registration, now())
        call.respond(HttpStatusCode.NoContent)
    }

    delete("/v1/devices/{installationId}") {
        devices.delete(requireInstallationId(call.parameters["installationId"]))
        call.respond(HttpStatusCode.NoContent)
    }
}
