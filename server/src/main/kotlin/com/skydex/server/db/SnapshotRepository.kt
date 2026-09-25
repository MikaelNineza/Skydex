package com.skydex.server.db

import com.skydex.shared.model.StatPoint
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Reads and writes the [Snapshots] table. */
class SnapshotRepository(private val db: Database) {
    suspend fun insert(uuid: String, profileId: String, point: StatPoint) {
        db.query {
            Snapshots.insert {
                it[Snapshots.uuid] = uuid
                it[Snapshots.profileId] = profileId
                it[takenAt] = point.takenAt
                it[skyblockLevel] = point.skyblockLevel
                it[purse] = point.purse
                it[bankBalance] = point.bankBalance
                it[skillAverage] = point.skillAverage
                it[catacombsLevel] = point.catacombsLevel
                it[totalSlayerXp] = point.totalSlayerXp
            }
        }
    }

    /** Snapshots taken at or after [sinceMillis], oldest first. */
    suspend fun history(uuid: String, profileId: String, sinceMillis: Long): List<StatPoint> =
        db.query {
            Snapshots.selectAll()
                .where {
                    (Snapshots.uuid eq uuid) and (Snapshots.profileId eq profileId) and
                        (Snapshots.takenAt greaterEq sinceMillis)
                }
                .orderBy(Snapshots.takenAt, SortOrder.ASC)
                .map {
                    StatPoint(
                        takenAt = it[Snapshots.takenAt],
                        skyblockLevel = it[Snapshots.skyblockLevel],
                        purse = it[Snapshots.purse],
                        bankBalance = it[Snapshots.bankBalance],
                        skillAverage = it[Snapshots.skillAverage],
                        catacombsLevel = it[Snapshots.catacombsLevel],
                        totalSlayerXp = it[Snapshots.totalSlayerXp],
                    )
                }
        }

    /** Deletes snapshots taken before [beforeMillis]. Returns how many were deleted. */
    suspend fun prune(beforeMillis: Long): Int =
        db.query { Snapshots.deleteWhere { takenAt less beforeMillis } }
}
