package com.skydex.server.db

import com.skydex.shared.model.Crop
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

/** A registered app installation. */
data class Device(val installationId: String, val registration: DeviceRegistration)

/** A profile some device wants stats history for. */
data class TrackedProfile(val uuid: String, val profileId: String)

/** Reads and writes the [Devices] table. */
class DeviceRepository(private val db: Database) {
    /** Inserts or replaces the registration for [installationId]. */
    suspend fun upsert(installationId: String, registration: DeviceRegistration, now: Long) {
        db.query {
            Devices.upsert {
                it[Devices.installationId] = installationId
                it[fcmToken] = registration.fcmToken
                it[trackedUuid] = registration.trackedUuid
                it[trackedProfileId] = registration.trackedProfileId
                it[subscribedEvents] = registration.subscribedEvents.joinToString(",") { type -> type.name }
                it[leadMinutes] = registration.leadMinutes
                it[jacobCrops] = registration.jacobCrops.joinToString(",") { crop -> crop.name }
                it[updatedAt] = now
            }
        }
    }

    /** Removes the device and, by cascade, its sent alerts. Returns false if it wasn't registered. */
    suspend fun delete(installationId: String): Boolean =
        db.query { Devices.deleteWhere { Devices.installationId eq installationId } > 0 }

    suspend fun find(installationId: String): Device? =
        db.query {
            Devices.selectAll().where { Devices.installationId eq installationId }.singleOrNull()?.toDevice()
        }

    /** Devices subscribed to at least one event. */
    suspend fun subscribed(): List<Device> =
        db.query {
            Devices.selectAll().where { Devices.subscribedEvents neq "" }.map { it.toDevice() }
        }

    /** Every distinct profile tracked by at least one device. */
    suspend fun trackedProfiles(): List<TrackedProfile> =
        db.query {
            Devices.select(Devices.trackedUuid, Devices.trackedProfileId)
                .where { Devices.trackedUuid.isNotNull() and Devices.trackedProfileId.isNotNull() }
                .withDistinct()
                .map { TrackedProfile(it[Devices.trackedUuid]!!, it[Devices.trackedProfileId]!!) }
        }

    private fun ResultRow.toDevice() = Device(
        installationId = this[Devices.installationId],
        registration = DeviceRegistration(
            fcmToken = this[Devices.fcmToken],
            trackedUuid = this[Devices.trackedUuid],
            trackedProfileId = this[Devices.trackedProfileId],
            subscribedEvents = this[Devices.subscribedEvents].split(",")
                .mapNotNull { name -> EventType.entries.find { it.name == name } }
                .toSet(),
            leadMinutes = this[Devices.leadMinutes],
            jacobCrops = this[Devices.jacobCrops].split(",")
                .mapNotNull { name -> Crop.entries.find { it.name == name } }
                .toSet(),
        ),
    )
}
