package com.skydex.server.db

import com.skydex.shared.model.Crop
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
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
    private val sentAlerts = SentAlertRepository(db)

    @Test
    fun deviceUpsertReplacesAndDeleteRemoves() = runBlocking {
        devices.upsert("a", DeviceRegistration(fcmToken = "t1"), now = 1)
        val updated = DeviceRegistration(
            fcmToken = "t2",
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
    fun subscribedDevices() = runBlocking {
        devices.upsert("none", DeviceRegistration(fcmToken = "t"), now = 0)
        val subscribed = DeviceRegistration(fcmToken = "t", subscribedEvents = setOf(EventType.JACOBS_CONTEST))
        devices.upsert("b", subscribed, now = 0)
        devices.upsert("c", subscribed, now = 0)

        assertEquals(setOf("b", "c"), devices.subscribed().map { it.installationId }.toSet())
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
    fun initDatabaseIsIdempotentAndMigratesAnOldSchema() = runBlocking {
        val dataSource = createDataSource(
            url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            user = "sa",
            password = "",
        )
        // Tables from before jacob_crops was added and while stats history existed: a device subscribed to events,
        // one registered only to track a profile, and a snapshot.
        dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute(
                    "CREATE TABLE devices (installation_id VARCHAR(64) PRIMARY KEY, fcm_token VARCHAR(4096) NOT NULL, " +
                        "tracked_uuid VARCHAR(32), tracked_profile_id VARCHAR(64), subscribed_events TEXT NOT NULL, " +
                        "lead_minutes INT NOT NULL, updated_at BIGINT NOT NULL)",
                )
                it.execute("INSERT INTO devices VALUES ('old', 't', 'u', 'p', 'DARK_AUCTION', 5, 0)")
                it.execute("INSERT INTO devices VALUES ('tracker', 't', 'u', 'p', '', 5, 0)")
                it.execute(
                    "CREATE TABLE snapshots (id BIGSERIAL PRIMARY KEY, uuid VARCHAR(32) NOT NULL, " +
                        "profile_id VARCHAR(64) NOT NULL, taken_at BIGINT NOT NULL, skyblock_level DOUBLE PRECISION)",
                )
                it.execute("INSERT INTO snapshots (uuid, profile_id, taken_at, skyblock_level) VALUES ('u', 'p', 1, 2.0)")
            }
            if (!connection.autoCommit) connection.commit()
        }
        initDatabase(dataSource)
        val db = initDatabase(dataSource)
        val repository = DeviceRepository(db)

        dataSource.connection.use { connection ->
            fun count(sql: String) = connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { it.next(); it.getInt(1) }
            }
            assertEquals(
                0,
                count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'snapshots'"),
            )
            assertEquals(
                0,
                count(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = 'devices' " +
                        "AND LOWER(COLUMN_NAME) LIKE 'tracked%'",
                ),
            )
            assertEquals(
                1,
                count(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = 'devices' " +
                        "AND LOWER(COLUMN_NAME) = 'jacob_crops'",
                ),
            )
        }
        assertEquals(
            DeviceRegistration(fcmToken = "t", subscribedEvents = setOf(EventType.DARK_AUCTION)),
            repository.find("old")?.registration,
        )
        assertNull(repository.find("tracker"))
        val withCrops = DeviceRegistration(fcmToken = "t", jacobCrops = setOf(Crop.MELON))
        repository.upsert("new", withCrops, now = 0)
        assertEquals(withCrops, repository.find("new")?.registration)
    }
}
