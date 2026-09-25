package com.skydex.shared.model

import kotlinx.serialization.Serializable

/** `GET /v1/players/{uuid}/profiles/{profileId}/history?days=30`: snapshots, oldest first. */
@Serializable
data class StatsHistory(
    val uuid: String,
    val profileId: String,
    val points: List<StatPoint>,
)

/** One stored snapshot of the numbers the stats screen charts. */
@Serializable
data class StatPoint(
    /** Unix millis when the snapshot was taken. */
    val takenAt: Long,
    val skyblockLevel: Double,
    val purse: Double,
    val bankBalance: Double? = null,
    /** Average level of the non-cosmetic skills. */
    val skillAverage: Double,
    val catacombsLevel: Int? = null,
    val totalSlayerXp: Long,
)
