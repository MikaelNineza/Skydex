package com.skydex.server.jobs

import com.skydex.server.db.Device
import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.SentAlert
import com.skydex.server.db.SentAlertRepository
import com.skydex.server.hypixel.LiveEventSource
import com.skydex.server.notifications.EventAlert
import com.skydex.server.notifications.PushResult
import com.skydex.server.notifications.PushSender
import com.skydex.shared.calendar.ActivePerks
import com.skydex.shared.calendar.SkyblockEvents
import com.skydex.shared.calendar.activePerks
import com.skydex.shared.model.Crop
import com.skydex.shared.model.EventType
import com.skydex.shared.model.SkyblockEvent
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

/** How long after an event starts its alert may still go out, so a late job run doesn't skip it. */
val ALERT_GRACE = 2.minutes

/** An alert that should be sent now: [device] wants to know about [event]. */
data class DueAlert(val device: Device, val event: SkyblockEvent)

/**
 * The alerts due at [now] for devices with a push token: [event][SkyblockEvent] types a device subscribes to,
 * whose lead time has been reached, that started less than [graceMillis] ago at most, and that aren't in
 * [alreadySent].
 *
 * A device with a Jacob's crop filter only gets contests whose crops ([contestCrops], by start time) include one of
 * its crops; contests with unknown crops are skipped for it.
 */
fun dueAlerts(
    now: Long,
    events: List<SkyblockEvent>,
    devices: List<Device>,
    alreadySent: Set<SentAlert>,
    graceMillis: Long = ALERT_GRACE.inWholeMilliseconds,
    contestCrops: Map<Long, List<Crop>> = emptyMap(),
): List<DueAlert> = devices.filter { it.registration.fcmToken.isNotBlank() }.flatMap { device ->
    val registration = device.registration
    events
        .filter { it.type in registration.subscribedEvents }
        .filter { event ->
            event.type != EventType.JACOBS_CONTEST || registration.jacobCrops.isEmpty() ||
                contestCrops[event.startsAt].orEmpty().any { it in registration.jacobCrops }
        }
        .filter { now >= it.startsAt - registration.leadMinutes.minutes.inWholeMilliseconds }
        .filter { now < it.startsAt + graceMillis }
        .filter { SentAlert(device.installationId, it.type, it.startsAt) !in alreadySent }
        .map { DueAlert(device, it) }
}

/** Notification text for [event] as seen at [now], e.g. "Starts in 5 minutes", followed by [crops] if known. */
fun alertBody(event: SkyblockEvent, now: Long, crops: List<Crop>? = null): String {
    val minutes = Math.ceilDiv(event.startsAt - now, 60_000L)
    val countdown = when {
        minutes <= 0 -> "Starting now"
        minutes == 1L -> "Starts in 1 minute"
        else -> "Starts in $minutes minutes"
    }
    return if (crops.isNullOrEmpty()) countdown else "$countdown · ${crops.joinToString { it.displayName }}"
}

/** Sends each device a push shortly before the events it subscribes to, once per event occurrence. */
class EventAlertJob(
    private val devices: DeviceRepository,
    private val sentAlerts: SentAlertRepository,
    private val pushSender: PushSender,
    /** Mayor perks and contest crops; without it perk-dependent events are skipped and crop filters never match. */
    private val live: LiveEventSource? = null,
    private val upcomingEvents: (nowMillis: Long, windowMillis: Long, perks: ActivePerks) -> List<SkyblockEvent> =
        SkyblockEvents::upcoming,
) {
    private val log = LoggerFactory.getLogger(EventAlertJob::class.java)

    suspend fun runOnce(now: Long) {
        val graceMillis = ALERT_GRACE.inWholeMilliseconds
        sentAlerts.prune(now - graceMillis)

        val subscribed = devices.subscribed()
        if (subscribed.isEmpty()) return
        val maxLead = subscribed.maxOf { it.registration.leadMinutes }.minutes.inWholeMilliseconds
        // Start the window a grace period back so instantaneous events that just happened still count.
        val perks = liveOr(ActivePerks.NONE, "mayor") { live?.mayor()?.activePerks() ?: ActivePerks.NONE }
        val events = upcomingEvents(now - graceMillis, graceMillis + maxLead, perks)
        // Crops feed both the crop filters and the push body, so fetch them for any Jacob's contest subscriber.
        val wantsCrops = subscribed.any { EventType.JACOBS_CONTEST in it.registration.subscribedEvents }
        val contestCrops = if (!wantsCrops) {
            emptyMap()
        } else {
            liveOr(emptyMap(), "contests") { live?.contests().orEmpty().associate { it.startsAt to it.crops } }
        }
        val alreadySent = sentAlerts.sentSince(now - graceMillis)
        val due = dueAlerts(now, events, subscribed, alreadySent, graceMillis, contestCrops)

        for ((device, event) in due) {
            try {
                val crops = contestCrops[event.startsAt]?.takeIf { event.type == EventType.JACOBS_CONTEST }
                val alert = EventAlert(event, title = event.type.displayName, body = alertBody(event, now, crops))
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

    /** Runs [fetch], logging a failure and falling back to [fallback] so calendar-only alerts still go out. */
    private suspend fun <T> liveOr(fallback: T, what: String, fetch: suspend () -> T): T =
        try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not load {} for alerts: {}", what, e.toString())
            fallback
        }
}
