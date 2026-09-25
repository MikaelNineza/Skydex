package com.skydex.server

import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.SentAlertRepository
import com.skydex.server.db.SnapshotRepository
import com.skydex.server.db.createDataSource
import com.skydex.server.db.initDatabase
import com.skydex.server.hypixel.ProfileSource
import com.skydex.server.jobs.EventAlertJob
import com.skydex.server.jobs.SnapshotJob
import com.skydex.server.jobs.launchEvery
import com.skydex.server.notifications.FcmPushSender
import com.skydex.server.notifications.LoggingPushSender
import com.skydex.server.routes.deviceRoutes
import com.skydex.server.routes.eventRoutes
import com.skydex.server.routes.historyRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Wires the event route, and when `database.enabled` is true also the database, device and history routes,
 * the snapshot job (fed by [profileSource]) and the event alert job.
 *
 * Without `database.enabled` (as in tests, whose config is empty) the server runs without Postgres.
 */
fun Application.configureData(profileSource: ProfileSource) {
    routing { eventRoutes() }

    val config = environment.config
    fun property(path: String): String? = config.propertyOrNull(path)?.getString()

    if (property("database.enabled")?.toBoolean() != true) {
        log.warn("database.enabled is not true: device registration, history and push alerts are off")
        return
    }

    val dataSource = createDataSource(
        url = property("database.url") ?: error("database.url is not set"),
        user = property("database.user").orEmpty(),
        password = property("database.password").orEmpty(),
    )
    monitor.subscribe(ApplicationStopped) { dataSource.close() }
    val db = initDatabase(dataSource)
    val devices = DeviceRepository(db)
    val snapshots = SnapshotRepository(db)
    val sentAlerts = SentAlertRepository(db)

    val credentialsPath = property("firebase.credentialsPath").orEmpty()
    val pushSender = if (credentialsPath.isBlank()) {
        log.warn("firebase.credentialsPath is not set: push alerts will only be logged")
        LoggingPushSender()
    } else {
        FcmPushSender(credentialsPath)
    }

    routing {
        deviceRoutes(devices)
        historyRoutes(snapshots)
    }

    // Launched in the Application scope, so they are cancelled when the server stops.
    val snapshotJob = SnapshotJob(profileSource, devices, snapshots)
    val snapshotInterval = (property("jobs.snapshotIntervalHours")?.toLong() ?: 3).hours
    launchEvery(snapshotInterval, LoggerFactory.getLogger(SnapshotJob::class.java)) {
        snapshotJob.runOnce(System.currentTimeMillis())
    }

    val alertJob = EventAlertJob(devices, sentAlerts, pushSender)
    val alertInterval = (property("jobs.alertIntervalSeconds")?.toLong() ?: 60).seconds
    launchEvery(alertInterval, LoggerFactory.getLogger(EventAlertJob::class.java)) {
        alertJob.runOnce(System.currentTimeMillis())
    }
}
