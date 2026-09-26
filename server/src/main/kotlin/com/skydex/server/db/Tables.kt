package com.skydex.server.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** One row per app installation: its push token and what it wants to hear about. */
object Devices : Table("devices") {
    val installationId = varchar("installation_id", 64)
    val fcmToken = varchar("fcm_token", 4096)

    /** Comma-separated [com.skydex.shared.model.EventType] names. */
    val subscribedEvents = text("subscribed_events")
    val leadMinutes = integer("lead_minutes")

    /** Comma-separated [com.skydex.shared.model.Crop] names; empty = any crop. */
    val jacobCrops = text("jacob_crops").default("")

    /** Unix millis. */
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(installationId)
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
