package com.skydex.server.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** One row per app installation: its push token and what it wants to hear about. */
object Devices : Table("devices") {
    val installationId = varchar("installation_id", 64)
    val fcmToken = varchar("fcm_token", 4096)
    val trackedUuid = varchar("tracked_uuid", 32).nullable()
    val trackedProfileId = varchar("tracked_profile_id", 64).nullable()

    /** Comma-separated [com.skydex.shared.model.EventType] names. */
    val subscribedEvents = text("subscribed_events")
    val leadMinutes = integer("lead_minutes")

    /** Comma-separated [com.skydex.shared.model.Crop] names; empty = any crop. */
    val jacobCrops = text("jacob_crops").default("")

    /** Unix millis. */
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(installationId)
}

/** Periodic copies of a profile's charted numbers; one row per [com.skydex.shared.model.StatPoint]. */
object Snapshots : Table("snapshots") {
    val id = long("id").autoIncrement()
    val uuid = varchar("uuid", 32)
    val profileId = varchar("profile_id", 64)

    /** Unix millis. */
    val takenAt = long("taken_at")
    val skyblockLevel = double("skyblock_level")
    val purse = double("purse")
    val bankBalance = double("bank_balance").nullable()
    val skillAverage = double("skill_average")
    val catacombsLevel = integer("catacombs_level").nullable()
    val totalSlayerXp = long("total_slayer_xp")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, uuid, profileId, takenAt)
    }
}

/** Event pushes already sent. The primary key guarantees a device never gets the same alert twice. */
object SentAlerts : Table("sent_alerts") {
    val installationId = varchar("installation_id", 64)
        .references(Devices.installationId, onDelete = ReferenceOption.CASCADE)
    val eventType = varchar("event_type", 32)

    /** Unix millis of the event occurrence the alert was for. */
    val startsAt = long("starts_at")

    override val primaryKey = PrimaryKey(installationId, eventType, startsAt)
}
