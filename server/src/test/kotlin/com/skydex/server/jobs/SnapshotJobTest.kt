package com.skydex.server.jobs

import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.SnapshotRepository
import com.skydex.server.db.testDatabase
import com.skydex.server.hypixel.PlayerNotFoundException
import com.skydex.server.hypixel.ProfileSource
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.PlayerProfiles
import com.skydex.shared.model.SkillLevel
import com.skydex.shared.model.SkyblockProfile
import com.skydex.shared.model.SlayerLevel
import com.skydex.shared.model.StatPoint
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotJobTest {
    private val goodUuid = "0123456789abcdef0123456789abcdef"
    private val badUuid = "ffffffffffffffffffffffffffffffff"
    private val profileId = "fedcba9876543210fedcba9876543210"

    private fun skill(name: String, level: Int) =
        SkillLevel(name, experience = 0.0, level, maxLevel = 60, progress = 0.0)

    private val profile = SkyblockProfile(
        profileId = profileId,
        cuteName = "Mango",
        uuid = goodUuid,
        username = "Steve",
        skyblockLevel = 212.45,
        purse = 1_000.0,
        bankBalance = 5_000.0,
        fairySouls = 200,
        skills = listOf(skill("farming", 50), skill("mining", 40), skill("runecrafting", 25), skill("social", 20)),
        slayers = listOf(SlayerLevel("zombie", 1_000_000, 9), SlayerLevel("wolf", 500, 2)),
        catacombs = skill("catacombs", 33),
        fetchedAt = 0,
    )

    @Test
    fun statPointAveragesNonCosmeticSkillsAndSumsSlayerXp() {
        assertEquals(
            StatPoint(
                takenAt = 42,
                skyblockLevel = 212.45,
                purse = 1_000.0,
                bankBalance = 5_000.0,
                skillAverage = 45.0,
                catacombsLevel = 33,
                totalSlayerXp = 1_000_500,
            ),
            statPoint(profile, takenAt = 42),
        )
        assertEquals(0.0, statPoint(profile.copy(skills = emptyList()), takenAt = 0).skillAverage)
    }

    @Test
    fun runOnceSnapshotsTrackedProfilesSkippingFailuresAndPrunes() = runBlocking {
        val db = testDatabase()
        val devices = DeviceRepository(db)
        val snapshots = SnapshotRepository(db)
        for (uuid in listOf(goodUuid, badUuid)) {
            devices.upsert(uuid, DeviceRegistration("t", trackedUuid = uuid, trackedProfileId = profileId), now = 0)
        }
        val source = object : ProfileSource {
            override suspend fun playerProfiles(nameOrUuid: String): PlayerProfiles = error("unused")

            override suspend fun profile(uuid: String, profileId: String): SkyblockProfile =
                if (uuid == goodUuid) profile else throw PlayerNotFoundException(uuid)
        }
        val now = SnapshotJob.RETENTION.inWholeMilliseconds + 1_000
        snapshots.insert(goodUuid, profileId, statPoint(profile, takenAt = 999))

        SnapshotJob(source, devices, snapshots).runOnce(now)

        assertEquals(listOf(now), snapshots.history(goodUuid, profileId, sinceMillis = 0).map { it.takenAt })
        assertEquals(emptyList(), snapshots.history(badUuid, profileId, sinceMillis = 0))
    }
}
