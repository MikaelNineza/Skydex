package com.skydex.server.jobs

import com.skydex.server.db.Device
import com.skydex.server.db.DeviceRepository
import com.skydex.server.db.SentAlert
import com.skydex.server.db.SentAlertRepository
import com.skydex.server.db.testDatabase
import com.skydex.server.notifications.EventAlert
import com.skydex.server.notifications.PushResult
import com.skydex.server.notifications.PushSender
import com.skydex.server.hypixel.LiveEventSource
import com.skydex.server.hypixel.UpstreamException
import com.skydex.shared.calendar.ActivePerks
import com.skydex.shared.calendar.SkyblockDate
import com.skydex.shared.calendar.termBounds
import com.skydex.shared.model.Crop
import com.skydex.shared.model.DeviceRegistration
import com.skydex.shared.model.EventType
import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.Mayor
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.Minister
import com.skydex.shared.model.Perk
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
        val job = EventAlertJob(devices, SentAlertRepository(db), sender, upcomingEvents = { _, _, _ -> listOf(auction) })

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

    // --- Jacob's crop filters and mayor-dependent events ---

    private val contest = SkyblockEvent(EventType.JACOBS_CONTEST, startsAt = start, endsAt = start + 20 * MINUTE)

    private fun cropDevice(id: String, vararg crops: Crop) = Device(
        id,
        DeviceRegistration(
            fcmToken = "token-$id",
            subscribedEvents = setOf(EventType.JACOBS_CONTEST),
            leadMinutes = 5,
            jacobCrops = crops.toSet(),
        ),
    )

    @Test
    fun cropFilterDecidesWhichContestsAreDue() {
        val wheat = cropDevice("wheat", Crop.WHEAT, Crop.MELON)
        val any = cropDevice("any")
        fun due(crops: Map<Long, List<Crop>>) =
            dueAlerts(start - MINUTE, listOf(contest), listOf(wheat, any), emptySet(), contestCrops = crops)
                .map { it.device.installationId }

        // Match on one of the three crops.
        assertEquals(listOf("wheat", "any"), due(mapOf(start to listOf(Crop.CARROT, Crop.WHEAT, Crop.POTATO))))
        // Mismatch: only the unfiltered device.
        assertEquals(listOf("any"), due(mapOf(start to listOf(Crop.CACTUS, Crop.CARROT, Crop.POTATO))))
        // Crops unknown (no entry, or another contest's entry): the filtered device isn't alerted.
        assertEquals(listOf("any"), due(emptyMap()))
        assertEquals(listOf("any"), due(mapOf(start + 60 * MINUTE to listOf(Crop.WHEAT))))
        assertEquals(listOf("any"), due(mapOf(start to emptyList())))
    }

    @Test
    fun cropFilterOnlyAppliesToContests() {
        val device = Device(
            "d",
            DeviceRegistration(
                fcmToken = "t",
                subscribedEvents = setOf(EventType.DARK_AUCTION),
                jacobCrops = setOf(Crop.WHEAT),
            ),
        )
        assertEquals(listOf(DueAlert(device, auction)), dueAlerts(start, listOf(auction), listOf(device), emptySet()))
    }

    @Test
    fun alertBodyListsContestCrops() {
        assertEquals(
            "Starts in 5 minutes · Wheat, Carrot, Potato",
            alertBody(contest, start - 5 * MINUTE, listOf(Crop.WHEAT, Crop.CARROT, Crop.POTATO)),
        )
        assertEquals("Starts in 5 minutes", alertBody(contest, start - 5 * MINUTE, emptyList()))
        assertEquals("Starts in 5 minutes", alertBody(contest, start - 5 * MINUTE, null))
    }

    /** A [LiveEventSource] that fails when [mayorStatus] or [contestList] is null. */
    private class FakeLive(var mayorStatus: MayorStatus?, var contestList: List<JacobContest>?) : LiveEventSource {
        var mayorCalls = 0
        var contestCalls = 0

        override suspend fun mayor(): MayorStatus {
            mayorCalls++
            return mayorStatus ?: throw UpstreamException("Hypixel returned 500")
        }

        override suspend fun contests(): List<JacobContest> {
            contestCalls++
            return contestList ?: throw UpstreamException("elitebot returned 500")
        }
    }

    private class RecordingSender : PushSender {
        val pushed = mutableListOf<Pair<String, EventAlert>>()

        override suspend fun send(fcmToken: String, alert: EventAlert): PushResult {
            pushed += fcmToken to alert
            return PushResult.SENT
        }
    }

    private fun mayor(vararg perkNames: String, minister: Minister? = null): MayorStatus {
        val (termStart, termEnd) = termBounds(515)
        return MayorStatus(
            mayor = Mayor("someone", "Someone", perkNames.map { Perk(it, "") }),
            minister = minister,
            electionYear = 515,
            termStartsAt = termStart,
            termEndsAt = termEnd,
        )
    }

    @Test
    fun runOnceFiltersContestsByCropsAndListsThemInTheBody() = runBlocking {
        val db = testDatabase()
        val devices = DeviceRepository(db)
        devices.upsert("cactus", cropDevice("cactus", Crop.CACTUS).registration, now = 0)
        devices.upsert("wheat", cropDevice("wheat", Crop.WHEAT).registration, now = 0)
        devices.upsert("any", cropDevice("any").registration, now = 0)
        val live = FakeLive(mayor(), listOf(JacobContest(start, listOf(Crop.CACTUS, Crop.CARROT, Crop.NETHER_WART))))
        val sender = RecordingSender()
        val job = EventAlertJob(devices, SentAlertRepository(db), sender, live, upcomingEvents = { _, _, _ -> listOf(contest) })

        job.runOnce(start - 3 * MINUTE)

        assertEquals(listOf("token-any", "token-cactus"), sender.pushed.map { it.first }.sorted())
        sender.pushed.forEach {
            assertEquals("Jacob's Farming Contest", it.second.title)
            assertEquals("Starts in 3 minutes · Cactus, Carrot, Nether Wart", it.second.body)
        }
    }

    @Test
    fun liveFailuresStillSendCalendarAlerts() = runBlocking {
        val db = testDatabase()
        val devices = DeviceRepository(db)
        devices.upsert("auction", device("auction", 5, EventType.DARK_AUCTION).registration, now = 0)
        devices.upsert("wheat", cropDevice("wheat", Crop.WHEAT).registration, now = 0)
        devices.upsert("any", cropDevice("any").registration, now = 0)
        val live = FakeLive(mayorStatus = null, contestList = null)
        val sender = RecordingSender()
        val perksSeen = mutableListOf<ActivePerks>()
        val job = EventAlertJob(devices, SentAlertRepository(db), sender, live, upcomingEvents = { _, _, perks ->
            perksSeen += perks
            listOf(auction, contest)
        })

        job.runOnce(start - 3 * MINUTE)

        assertEquals(listOf(ActivePerks.NONE), perksSeen)
        assertEquals(1, live.mayorCalls)
        assertEquals(1, live.contestCalls)
        // Unknown crops: the filtered device gets nothing, the unfiltered one a crop-less body.
        assertEquals(
            listOf("token-any" to "Starts in 3 minutes", "token-auction" to "Starts in 3 minutes"),
            sender.pushed.map { it.first to it.second.body }.sortedBy { it.first },
        )
    }

    @Test
    fun contestsAreOnlyFetchedForContestSubscribers() = runBlocking {
        val db = testDatabase()
        val devices = DeviceRepository(db)
        devices.upsert("auction", device("auction", 5, EventType.DARK_AUCTION).registration, now = 0)
        val live = FakeLive(mayor(), emptyList())
        val job = EventAlertJob(devices, SentAlertRepository(db), RecordingSender(), live, upcomingEvents = { _, _, _ -> listOf(auction) })

        job.runOnce(start - 3 * MINUTE)

        assertEquals(0, live.contestCalls)
    }

    @Test
    fun miningFiestaAlertOnlyWhileThePerkIsActive() = runBlocking {
        // First Mining Fiesta of the term won in 515 (real calendar, no fake events).
        val fiesta = SkyblockDate(516, 4, 1).toMillis()
        suspend fun alertsWith(mayorStatus: MayorStatus?): List<String> {
            val db = testDatabase()
            val devices = DeviceRepository(db)
            devices.upsert("f", device("f", 5, EventType.MINING_FIESTA).registration, now = 0)
            val sender = RecordingSender()
            EventAlertJob(devices, SentAlertRepository(db), sender, FakeLive(mayorStatus, emptyList()))
                .runOnce(fiesta - 3 * MINUTE)
            return sender.pushed.map { it.second.title + ": " + it.second.body }
        }

        val cole = Minister("mining", "Cole", Perk("Mining Fiesta", "Schedules 5 Mining Fiestas.", minister = true))
        assertEquals(listOf("Mining Fiesta: Starts in 3 minutes"), alertsWith(mayor("Volume Trading", minister = cole)))
        assertEquals(listOf("Mining Fiesta: Starts in 3 minutes"), alertsWith(mayor("Mining Fiesta")))
        assertEquals(emptyList(), alertsWith(mayor("Volume Trading")))
        assertEquals(emptyList(), alertsWith(null))
        assertEquals(emptyList(), alertsWith(mayor("Mining Fiesta").copy(mayor = Mayor("jerry", "Jerry", listOf(Perk("Mining Fiesta", ""))))))
    }
}
