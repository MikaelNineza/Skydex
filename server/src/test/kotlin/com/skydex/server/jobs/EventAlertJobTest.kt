package com.skydex.server.jobs

import com.skydex.server.db.Device
import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.SentAlert
import com.skydex.server.db.SentAlertRepository
import com.skydex.server.db.testDatabase
import com.skydex.server.notifications.EventAlert
import com.skydex.server.notifications.PushResult
import com.skydex.server.notifications.PushSender
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import com.skydex.shared.model.SkyblockEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

class EventAlertJobTest {
    private val start = 1_000 * MINUTE
    private val auction = SkyblockEvent(EventType.DARK_AUCTION, startsAt = start, endsAt = start + 5 * MINUTE)
    private val interest = SkyblockEvent(EventType.BANK_INTEREST, startsAt = start, endsAt = start)

    private fun device(id: String, lead: Int, vararg types: EventType) =
        Device(id, DeviceRegistration(fcmToken = "token-$id", subscribedEvents = types.toSet(), leadMinutes = lead))

    @Test
    fun alertIsDueFromLeadTimeUntilGraceAfterStart() {
        val devices = listOf(device("a", 5, EventType.DARK_AUCTION))
        val grace = 2 * MINUTE
        fun due(now: Long) = dueAlerts(now, listOf(auction), devices, emptySet(), grace)

        assertEquals(emptyList(), due(start - 5 * MINUTE - 1))
        assertEquals(listOf(DueAlert(devices[0], auction)), due(start - 5 * MINUTE))
        assertEquals(1, due(start + grace - 1).size)
        assertEquals(emptyList(), due(start + grace))
    }

    @Test
    fun onlySubscribedUnsentAlertsAreDue() {
        val a = device("a", 10, EventType.DARK_AUCTION, EventType.BANK_INTEREST)
        val b = device("b", 1, EventType.DARK_AUCTION)
        val c = device("c", 10, EventType.JACOBS_CONTEST)
        val sent = setOf(SentAlert("a", EventType.DARK_AUCTION, start))

        val due = dueAlerts(start - 5 * MINUTE, listOf(auction, interest), listOf(a, b, c), sent)

        // b's lead time isn't reached yet, c isn't subscribed, a already got the auction alert.
        assertEquals(listOf(DueAlert(a, interest)), due)
    }

    @Test
    fun devicesWithoutTokenGetNoAlerts() {
        val tokenless = Device("t", DeviceRegistration(fcmToken = "", subscribedEvents = setOf(EventType.DARK_AUCTION)))

        assertEquals(emptyList(), dueAlerts(start, listOf(auction), listOf(tokenless), emptySet()))
    }

    @Test
    fun alertBodyCountsDownInMinutes() {
        assertEquals("Starts in 5 minutes", alertBody(auction, start - 5 * MINUTE))
        assertEquals("Starts in 5 minutes", alertBody(auction, start - 4 * MINUTE - 1))
        assertEquals("Starts in 1 minute", alertBody(auction, start - 30_000))
        assertEquals("Starting now", alertBody(auction, start + 1))
    }

    @Test
    fun runOnceSendsEachAlertOnceAndForgetsUnregisteredTokens() = runBlocking {
        val db = testDatabase()
        val devices = DeviceRepository(db)
        devices.upsert("ok", device("ok", 5, EventType.DARK_AUCTION).registration, now = 0)
        devices.upsert("gone", device("gone", 5, EventType.DARK_AUCTION).registration, now = 0)
        val pushed = mutableListOf<Pair<String, EventAlert>>()
        val sender = object : PushSender {
            override suspend fun send(fcmToken: String, alert: EventAlert): PushResult {
                pushed += fcmToken to alert
                return if (fcmToken == "token-gone") PushResult.UNREGISTERED else PushResult.SENT
            }
        }
        val job = EventAlertJob(devices, SentAlertRepository(db), sender, upcomingEvents = { _, _ -> listOf(auction) })

        job.runOnce(start - 3 * MINUTE)
        job.runOnce(start - 2 * MINUTE)

        // One push each across both runs: "ok" isn't alerted twice and "gone" was removed after the first.
        assertEquals(listOf("token-gone", "token-ok"), pushed.map { it.first }.sorted())
        val alert = pushed.first { it.first == "token-ok" }.second
        assertEquals("Dark Auction", alert.title)
        assertEquals("Starts in 3 minutes", alert.body)
        assertNull(devices.find("gone"))
        assertTrue(devices.find("ok") != null)
    }
}
