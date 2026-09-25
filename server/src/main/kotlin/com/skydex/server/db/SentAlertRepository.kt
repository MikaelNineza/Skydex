package com.skydex.server.db

import com.skydex.shared.model.EventType
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Identifies one alert: a device, and the event occurrence it was about. */
data class SentAlert(val installationId: String, val eventType: EventType, val startsAt: Long)

/** Reads and writes the [SentAlerts] table. */
class SentAlertRepository(private val db: Database) {
    /** Alerts for event occurrences starting at or after [sinceMillis]. */
    suspend fun sentSince(sinceMillis: Long): Set<SentAlert> =
        db.query {
            SentAlerts.selectAll().where { SentAlerts.startsAt greaterEq sinceMillis }.mapNotNull { row ->
                val type = EventType.entries.find { it.name == row[SentAlerts.eventType] } ?: return@mapNotNull null
                SentAlert(row[SentAlerts.installationId], type, row[SentAlerts.startsAt])
            }.toSet()
        }

    /** Records [alert] as sent; does nothing if it already was. */
    suspend fun record(alert: SentAlert) {
        db.query {
            SentAlerts.insertIgnore {
                it[installationId] = alert.installationId
                it[eventType] = alert.eventType.name
                it[startsAt] = alert.startsAt
            }
        }
    }

    /** Forgets alerts for events that started before [beforeMillis]. */
    suspend fun prune(beforeMillis: Long): Int =
        db.query { SentAlerts.deleteWhere { startsAt less beforeMillis } }
}
