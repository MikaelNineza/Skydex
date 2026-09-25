package com.skydex.server.jobs

import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.SnapshotRepository
import com.skydex.server.hypixel.ProfileSource
import com.skydex.shared.model.SkyblockProfile
import com.skydex.shared.model.StatPoint
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days

/** Records a [StatPoint] for every profile a device tracks, and drops snapshots older than a year. */
class SnapshotJob(
    private val profileSource: ProfileSource,
    private val devices: DeviceRepository,
    private val snapshots: SnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(SnapshotJob::class.java)

    suspend fun runOnce(now: Long) {
        for (tracked in devices.trackedProfiles()) {
            try {
                val profile = profileSource.profile(tracked.uuid, tracked.profileId)
                snapshots.insert(tracked.uuid, tracked.profileId, statPoint(profile, now))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Snapshot of {}/{} failed: {}", tracked.uuid, tracked.profileId, e.toString())
            }
        }
        val pruned = snapshots.prune(now - RETENTION.inWholeMilliseconds)
        if (pruned > 0) log.info("Pruned {} old snapshots", pruned)
    }

    companion object {
        val RETENTION = 365.days
    }
}

/** Skills left out of the skill average, as in game. */
private val COSMETIC_SKILLS = setOf("runecrafting", "social")

/** The numbers the stats screen charts, taken from [profile] at [takenAt]. */
fun statPoint(profile: SkyblockProfile, takenAt: Long): StatPoint = StatPoint(
    takenAt = takenAt,
    skyblockLevel = profile.skyblockLevel,
    purse = profile.purse,
    bankBalance = profile.bankBalance,
    skillAverage = profile.skills.filter { it.name !in COSMETIC_SKILLS }.map { it.level }.average()
        .takeUnless { it.isNaN() } ?: 0.0,
    catacombsLevel = profile.catacombs?.level,
    totalSlayerXp = profile.slayers.sumOf { it.experience },
)
