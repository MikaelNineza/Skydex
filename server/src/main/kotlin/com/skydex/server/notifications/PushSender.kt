package com.skydex.server.notifications

import com.skydex.shared.model.SkyblockEvent
import org.slf4j.LoggerFactory

/** A push telling a device that [event] is about to start. */
data class EventAlert(
    val event: SkyblockEvent,
    val title: String,
    val body: String,
)

enum class PushResult {
    SENT,

    /** The token is no longer valid; the device should be forgotten. */
    UNREGISTERED,
}

/** Delivers pushes to devices. Throws on failures other than an unregistered token. */
interface PushSender {
    suspend fun send(fcmToken: String, alert: EventAlert): PushResult
}

/** Used when Firebase isn't configured: logs each push instead of sending it. */
class LoggingPushSender : PushSender {
    private val log = LoggerFactory.getLogger(LoggingPushSender::class.java)

    override suspend fun send(fcmToken: String, alert: EventAlert): PushResult {
        log.info("Push (not sent, Firebase not configured) to {}: {} - {}", fcmToken.take(8), alert.title, alert.body)
        return PushResult.SENT
    }
}
