package com.skydex.server.jobs

import com.skydex.server.db.Device
import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.SentAlert
import com.skydex.server.db.SentAlertRepository
import com.skydex.server.notifications.EventAlert
import com.skydex.server.notifications.PushResult
import com.skydex.server.notifications.PushSender
import com.skydex.shared.calendar.SkyblockEvents
import com.skydex.shared.model.SkyblockEvent
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

/** How long after an event starts its alert may still go out, so a late job run doesn't skip it. */
val ALERT_GRACE = 2.minutes

/** An alert that should be sent now: [device] wants to know about [event]. */
data class DueAlert(val device: Device, val event: SkyblockEvent)

/**
 * The alerts due at [now]: [event][SkyblockEvent] types a device subscribes to, whose lead time has been reached,
 * that started less than [graceMillis] ago at most, and that aren't in [alreadySent].
 */
fun dueAlerts(
    now: Long,
    events: List<SkyblockEvent>,
    devices: List<Device>,
    alreadySent: Set<SentAlert>,
    graceMillis: Long = ALERT_GRACE.inWholeMilliseconds,
): List<DueAlert> = devices.flatMap { device ->
    val registration = device.registration
    events
        .filter { it.type in registration.subscribedEvents }
        .filter { now >= it.startsAt - registration.leadMinutes.minutes.inWholeMilliseconds }
        .filter { now < it.startsAt + graceMillis }
        .filter { SentAlert(device.installationId, it.type, it.startsAt) !in alreadySent }
        .map { DueAlert(device, it) }
}

/** Notification text for [event] as seen at [now], e.g. "Starts in 5 minutes". */
fun alertBody(event: SkyblockEvent, now: Long): String {
    val minutes = Math.ceilDiv(event.startsAt - now, 60_000L)
    return when {
        minutes <= 0 -> "Starting now"
        minutes == 1L -> "Starts in 1 minute"
        else -> "Starts in $minutes minutes"
    }
}

/** Sends each device a push shortly before the events it subscribes to, once per event occurrence. */
class EventAlertJob(
    private val devices: DeviceRepository,
    private val sentAlerts: SentAlertRepository,
    private val pushSender: PushSender,
    private val upcomingEvents: (nowMillis: Long, windowMillis: Long) -> List<SkyblockEvent> = SkyblockEvents::upcoming,
) {
    private val log = LoggerFactory.getLogger(EventAlertJob::class.java)

    suspend fun runOnce(now: Long) {
        val graceMillis = ALERT_GRACE.inWholeMilliseconds
        sentAlerts.prune(now - graceMillis)

        val subscribed = devices.subscribed()
        if (subscribed.isEmpty()) return
        val maxLead = subscribed.maxOf { it.registration.leadMinutes }.minutes.inWholeMilliseconds
        // Start the window a grace period back so instantaneous events that just happened still count.
        val events = upcomingEvents(now - graceMillis, graceMillis + maxLead)
        val due = dueAlerts(now, events, subscribed, sentAlerts.sentSince(now - graceMillis), graceMillis)

        for ((device, event) in due) {
            try {
                val alert = EventAlert(event, title = event.type.displayName, body = alertBody(event, now))
                when (pushSender.send(device.registration.fcmToken, alert)) {
                    PushResult.SENT -> sentAlerts.record(SentAlert(device.installationId, event.type, event.startsAt))
                    PushResult.UNREGISTERED -> {
                        log.info("Removing device {}: FCM token unregistered", device.installationId)
                        devices.delete(device.installationId)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Alert for {} to {} failed: {}", event.type, device.installationId, e.toString())
            }
        }
    }
}
