package com.skydex.server.db

import com.skydex.shared.model.Crop
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import com.skydex.shared.model.StatPoint
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoriesTest {
    private val db = testDatabase()
    private val devices = DeviceRepository(db)
    private val snapshots = SnapshotRepository(db)
    private val sentAlerts = SentAlertRepository(db)

    private val uuid = "0123456789abcdef0123456789abcdef"
    private val profileId = "fedcba9876543210fedcba9876543210"

    @Test
    fun deviceUpsertReplacesAndDeleteRemoves() = runBlocking {
        devices.upsert("a", DeviceRegistration(fcmToken = "t1"), now = 1)
        val updated = DeviceRegistration(
            fcmToken = "t2",
            trackedUuid = uuid,
            trackedProfileId = profileId,
            subscribedEvents = setOf(EventType.DARK_AUCTION, EventType.BANK_INTEREST),
            leadMinutes = 10,
        )
        devices.upsert("a", updated, now = 2)

        assertEquals(Device("a", updated), devices.find("a"))
        assertTrue(devices.delete("a"))
        assertFalse(devices.delete("a"))
        assertNull(devices.find("a"))
    }

    @Test
    fun subscribedAndTrackedProfiles() = runBlocking {
        devices.upsert("none", DeviceRegistration(fcmToken = "t"), now = 0)
        val tracking = DeviceRegistration(
            fcmToken = "t",
            trackedUuid = uuid,
            trackedProfileId = profileId,
            subscribedEvents = setOf(EventType.JACOBS_CONTEST),
        )
        devices.upsert("b", tracking, now = 0)
        devices.upsert("c", tracking, now = 0)

        assertEquals(setOf("b", "c"), devices.subscribed().map { it.installationId }.toSet())
        assertEquals(listOf(TrackedProfile(uuid, profileId)), devices.trackedProfiles())
    }

    @Test
    fun snapshotHistoryIsOldestFirstAndPruned() = runBlocking {
        for (t in listOf(300L, 100L, 200L)) snapshots.insert(uuid, profileId, point(t))
        snapshots.insert(uuid, "other", point(250))

        assertEquals(listOf(200L, 300L), snapshots.history(uuid, profileId, sinceMillis = 150).map { it.takenAt })
        assertEquals(point(100), snapshots.history(uuid, profileId, sinceMillis = 0).first())

        assertEquals(2, snapshots.prune(beforeMillis = 250))
        assertEquals(listOf(300L), snapshots.history(uuid, profileId, sinceMillis = 0).map { it.takenAt })
    }

    @Test
    fun sentAlertsAreRecordedOnceAndCascadeWithTheirDevice() = runBlocking {
        devices.upsert("a", DeviceRegistration(fcmToken = "t"), now = 0)
        val alert = SentAlert("a", EventType.DARK_AUCTION, startsAt = 1_000)
        sentAlerts.record(alert)
        sentAlerts.record(alert)
        sentAlerts.record(alert.copy(startsAt = 10))

        assertEquals(setOf(alert), sentAlerts.sentSince(500))
        assertEquals(1, sentAlerts.prune(beforeMillis = 500))

        devices.delete("a")
        assertEquals(emptySet(), sentAlerts.sentSince(0))
    }

    @Test
    fun jacobCropsRoundTrip() = runBlocking {
        val registration = DeviceRegistration(
            fcmToken = "t",
            subscribedEvents = setOf(EventType.JACOBS_CONTEST),
            jacobCrops = setOf(Crop.WHEAT, Crop.COCOA_BEANS, Crop.WILD_ROSE),
        )
        devices.upsert("crops", registration, now = 0)
        assertEquals(registration, devices.find("crops")?.registration)
        assertEquals(registration, devices.subscribed().single { it.installationId == "crops" }.registration)

        val cleared = registration.copy(jacobCrops = emptySet())
        devices.upsert("crops", cleared, now = 1)
        assertEquals(cleared, devices.find("crops")?.registration)
    }

    @Test
    fun initDatabaseIsIdempotentAndAddsTheCropColumn() = runBlocking {
        val dataSource = createDataSource(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = "",
        )
        // A table from before the jacob_crops column existed, with a device in it.
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "CREATE TABLE devices (installation_id VARCHAR(64) PRIMARY KEY, fcm_token VARCHAR(4096) NOT NULL, " +
                        "tracked_uuid VARCHAR(32), tracked_profile_id VARCHAR(64), subscribed_events TEXT NOT NULL, " +
                        "lead_minutes INT NOT NULL, updated_at BIGINT NOT NULL)",
                )
                it.execute("INSERT INTO devices VALUES ('old', 't', NULL, NULL, 'DARK_AUCTION', 5, 0)")
            }
            if (!connection.autoCommit) connection.commit()
        }
        initDatabase(dataSource)
        val db = initDatabase(dataSource)
        val repository = DeviceRepository(db)

        assertEquals(
            DeviceRegistration(fcmToken = "t", subscribedEvents = setOf(EventType.DARK_AUCTION)),
            repository.find("old")?.registration,
        )
        val withCrops = DeviceRegistration(fcmToken = "t", jacobCrops = setOf(Crop.MELON))
        repository.upsert("new", withCrops, now = 0)
        assertEquals(withCrops, repository.find("new")?.registration)
    }

    private fun point(takenAt: Long) = StatPoint(
        takenAt = takenAt,
        skyblockLevel = 100.5,
        purse = 1e6,
        bankBalance = null,
        skillAverage = 30.0,
        catacombsLevel = 25,
        totalSlayerXp = 123_456,
    )
}
