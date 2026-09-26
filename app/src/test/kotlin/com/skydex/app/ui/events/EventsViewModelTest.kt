package com.skydex.app.ui.events

import com.skydex.app.MainDispatcherRule
import com.skydex.app.data.remote.SkydexApi
import com.skydex.app.data.remote.SkydexJson
import com.skydex.app.data.remote.createHttpClient
import com.skydex.app.data.repository.EventsRepository
import com.skydex.app.respondJson
import com.skydex.app.ui.common.UiState
import com.skydex.shared.calendar.SkyblockDate
import com.skydex.shared.calendar.SkyblockEvents
import com.skydex.shared.calendar.termBounds
import com.skydex.shared.model.Crop
import com.skydex.shared.model.EventType
import com.skydex.shared.model.JacobContest
import com.skydex.shared.model.Mayor
import com.skydex.shared.model.MayorStatus
import com.skydex.shared.model.Minister
import com.skydex.shared.model.Perk
import com.skydex.shared.model.SkyblockEvent
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** A clock that follows the test's virtual time, starting at [start]. */
    private fun TestScope.clock(start: Long) = object : Clock() {
        override fun millis() = start + testScheduler.currentTime

        override fun instant(): Instant = Instant.ofEpochMilli(millis())

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId) = this
    }

    private fun EventsViewModel.content() = state.value as EventsUiState.Content

    private fun EventsUiState.Content.cards() = common + seasonal + rare

    private fun EventsViewModel.statuses() = content().cards().map { it.status }

    // --- Live data served by a MockEngine on the test scheduler, so requests complete deterministically. ---

    private val requests = mutableListOf<String>()
    private var handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { liveData(it) }

    private fun TestScope.repository(): EventsRepository {
        val config = MockEngineConfig().apply {
            dispatcher = UnconfinedTestDispatcher(testScheduler)
            addHandler { request ->
                requests += request.url.encodedPath
                handler(request)
            }
        }
        return EventsRepository(SkydexApi(createHttpClient(MockEngine(config), "http://test/")))
    }

    /** Inside the term won in the Year 515 election, on a whole real second. */
    private val now = SkyblockDate(516, 5, 10, 13, 0).toMillis() / 1000 * 1000

    private val term = termBounds(515)

    private val cole = Minister("mining", "Cole", Perk("Mining Fiesta", "Schedules 5 Mining Fiestas.", minister = true))

    private var mayor = MayorStatus(
        mayor = Mayor("economist", "Diaz", listOf(Perk("Volume Trading", "Doubled."))),
        minister = cole,
        electionYear = 515,
        termStartsAt = term.first,
        termEndsAt = term.second,
    )

    /** Crops for the next five Jacob's contests after [now]. */
    private val contests = SkyblockEvents.occurrences(EventType.JACOBS_CONTEST, now, 5).mapIndexed { i, contest ->
        JacobContest(contest.startsAt, listOf(Crop.entries[i], Crop.entries[i + 1], Crop.entries[i + 2]))
    }

    private fun MockRequestHandleScope.liveData(request: HttpRequestData): HttpResponseData =
        when (request.url.encodedPath) {
            "/v1/mayor" -> respondJson(SkydexJson.encodeToString(mayor))
            "/v1/contests" -> respondJson(SkydexJson.encodeToString(contests))
            else -> respondJson("""{"message":"Not found"}""", HttpStatusCode.NotFound)
        }

    private fun TestScope.liveViewModel() = EventsViewModel(clock(now), repository(), SkyblockEvents::occurrences)

    @Test
    fun `an unimplemented calendar shows the unavailable state`() = runTest {
        val vm = EventsViewModel(clock(0), null) { _, _, _, _ -> TODO("calendar task") }
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(EventsUiState.Unavailable, vm.state.value)
    }

    @Test
    fun `countdowns tick every second and past events are dropped`() = runTest {
        val start = 1_000_000L
        val events = listOf(
            SkyblockEvent(EventType.BANK_INTEREST, start - 1_000, start - 1_000),
            SkyblockEvent(EventType.JACOBS_CONTEST, start - 100_000, start + 200_000),
            SkyblockEvent(EventType.DARK_AUCTION, start + 65_000, start + 125_000),
        )
        val vm = EventsViewModel(clock(start), null) { type, _, count, _ ->
            events.filter { it.type == type }.take(count)
        }
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(listOf("Happening now · ends in 3m 20s", "in 1m 05s"), vm.statuses())

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("Happening now · ends in 3m 19s", "in 1m 04s"), vm.statuses())
    }

    @Test
    fun `one card per event type split into common, seasonal and rare`() = runTest {
        val vm = EventsViewModel(clock(now), null, SkyblockEvents::occurrences)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        val content = vm.content()
        val types = content.cards().map { it.type }
        assertEquals("one card per type", types.toSet().size, types.size)
        assertEquals(
            setOf(
                EventType.DARK_AUCTION, EventType.JACOBS_CONTEST, EventType.CULT_OF_THE_FALLEN_STAR,
                EventType.BANK_INTEREST,
            ),
            content.common.map { it.type }.toSet(),
        )
        assertEquals(
            setOf(
                EventType.TRAVELING_ZOO, EventType.SPOOKY_FESTIVAL, EventType.SEASON_OF_JERRY,
                EventType.NEW_YEAR_CELEBRATION, EventType.JERRYS_WORKSHOP, EventType.HOPPITYS_HUNT,
                EventType.ELECTION_OPEN, EventType.MAYOR_TERM_CHANGE,
            ),
            content.seasonal.map { it.type }.toSet(),
        )
        assertEquals(
            setOf(EventType.YEAR_OF_THE_SEAL, EventType.YEAR_OF_THE_WITCH, EventType.YEAR_OF_THE_PIG),
            content.rare.map { it.type }.toSet(),
        )
        assertEquals(content.common.sortedBy { it.next.startsAt }, content.common)
        assertEquals(content.seasonal.sortedBy { it.next.startsAt }, content.seasonal)
        assertEquals(content.rare.sortedBy { it.next.startsAt }, content.rare)
        // Without live data, gated events stay hidden and the mayor card reports it.
        assertTrue(content.mayor is UiState.Error)
        assertNull(content.termStatus)
        assertNull(content.detail)
    }

    @Test
    fun `mayor perks show gated events and contests carry crops`() = runTest {
        val vm = liveViewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        val content = vm.content()
        assertEquals(UiState.Content(mayor), content.mayor)
        assertFalse(content.perkpocalypse)
        // Perk-gated events are Common, not Seasonal or Rare.
        assertEquals(
            setOf(
                EventType.DARK_AUCTION, EventType.JACOBS_CONTEST, EventType.CULT_OF_THE_FALLEN_STAR,
                EventType.BANK_INTEREST, EventType.MINING_FIESTA,
            ),
            content.common.map { it.type }.toSet(),
        )
        assertEquals(8, content.seasonal.size)
        assertEquals(3, content.rare.size)
        assertFalse(content.cards().any { it.type == EventType.FISHING_FESTIVAL })
        val termStatus = assertNotNullAndGet(content.termStatus)
        assertTrue(termStatus, termStatus.matches(Regex("""Term ends in \d+d \d\dh \d\dm""")))
        val jacob = content.common.single { it.type == EventType.JACOBS_CONTEST }
        assertEquals(contests.first { it.startsAt == jacob.next.startsAt }.crops, jacob.crops)
        assertTrue(content.cards().filter { it.type != EventType.JACOBS_CONTEST }.all { it.crops == null })
    }

    @Test
    fun `tapping a card lists the next ten occurrences`() = runTest {
        val vm = liveViewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        vm.select(EventType.JACOBS_CONTEST)
        runCurrent()
        val detail = assertNotNullAndGet(vm.content().detail)
        assertEquals(EventType.JACOBS_CONTEST, detail.type)
        assertEquals(EventsViewModel.DETAIL_COUNT, detail.rows.size)
        assertEquals(10, detail.rows.size)
        assertEquals(detail.rows.sortedBy { it.next.startsAt }, detail.rows)
        detail.rows.forEach { assertEquals(EventType.JACOBS_CONTEST, it.type) }
        // Rows with published crops carry them; the rest are marked as not uploaded yet (empty).
        val cropsByStart = contests.associate { it.startsAt to it.crops }
        detail.rows.forEach { assertEquals(cropsByStart[it.next.startsAt] ?: emptyList<Crop>(), it.crops) }
        assertEquals(5, detail.rows.count { !it.crops.isNullOrEmpty() })

        vm.select(EventType.MINING_FIESTA)
        runCurrent()
        // Only four Fiestas left in this term.
        assertEquals(4, vm.content().detail!!.rows.size)

        vm.select(null)
        runCurrent()
        assertNull(vm.content().detail)
    }

    @Test
    fun `cards and detail rows show real time countdowns, never in game dates`() = runTest {
        val vm = liveViewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        // "in 1h 02m 03s" or "Happening now · ends in 4m 05s", and nothing else (e.g. no "Autumn 1st, Year 516").
        val countdown = """(\d+d \d\dh \d\dm|\d+h \d\dm \d\ds|\d+m \d\ds|\d+s)"""
        val allowed = Regex("""(in |Happening now · ends in )$countdown""")

        val rows = EventType.entries.flatMap { type ->
            vm.select(type)
            runCurrent()
            vm.content().detail!!.rows
        } + vm.content().cards()

        assertTrue(rows.isNotEmpty())
        rows.forEach { assertTrue(it.status, it.status.matches(allowed)) }
        // The in-game date label is gone from the model, not just hidden.
        assertFalse(EventCard::class.java.declaredFields.any { it.name == "dateLabel" })
    }

    @Test
    fun `jerry hides perk events`() = runTest {
        mayor = mayor.copy(mayor = Mayor("jerry", "Jerry", listOf(Perk("Mining Fiesta", "Random"))), minister = null)
        val vm = liveViewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertTrue(vm.content().perkpocalypse)
        assertFalse(vm.content().cards().any { it.type == EventType.MINING_FIESTA })
    }

    @Test
    fun `instantaneous events do not drop on their exact tick`() = runTest {
        val interest = SkyblockDate(516, 7, 1).toMillis()
        val vm = EventsViewModel(clock(interest - 2_000), null, SkyblockEvents::occurrences)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        fun bank() = vm.content().common.singleOrNull { it.type == EventType.BANK_INTEREST }

        assertEquals(interest, bank()?.next?.startsAt)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(interest, bank()?.next?.startsAt)
        advanceTimeBy(1_000)
        runCurrent()
        // Exactly at the instant: the card moves on to the next season instead of vanishing.
        assertEquals(SkyblockDate(516, 10, 1).toMillis(), bank()?.next?.startsAt)
    }

    @Test
    fun `live data is kept across resubscription and refreshed every five minutes`() = runTest {
        val vm = liveViewModel()
        val first = backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertEquals(listOf("/v1/mayor", "/v1/contests"), requests)

        // Leave the tab long enough for the flow to stop, then come back.
        first.cancel()
        advanceTimeBy(60_000)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertEquals(UiState.Content(mayor), vm.content().mayor)
        assertTrue(vm.content().common.any { it.type == EventType.MINING_FIESTA })
        assertEquals("no refetch within five minutes", 2, requests.size)

        // A failed refresh keeps the last good mayor.
        handler = { respondJson("""{"message":"Hypixel down"}""", HttpStatusCode.BadGateway) }
        advanceTimeBy(EventsViewModel.LIVE_REFRESH_MILLIS)
        runCurrent()
        assertTrue(requests.size > 2)
        assertEquals(UiState.Content(mayor), vm.content().mayor)
        // ...and the last good crops (the five contests span five hours; only ~6 minutes have passed).
        assertNotNull(vm.content().common.single { it.type == EventType.JACOBS_CONTEST }.crops)
    }

    @Test
    fun `a failed mayor load shows an error and retry recovers`() = runTest {
        handler = { request ->
            if (request.url.encodedPath == "/v1/mayor") {
                respondJson("""{"message":"Hypixel down"}""", HttpStatusCode.BadGateway)
            } else {
                liveData(request)
            }
        }
        val vm = liveViewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(UiState.Error("Hypixel down"), vm.content().mayor)
        assertFalse(vm.content().cards().any { it.type == EventType.MINING_FIESTA })
        // Crops still load without the mayor.
        assertNotNull(vm.content().common.single { it.type == EventType.JACOBS_CONTEST }.crops)

        handler = { liveData(it) }
        vm.retryLive()
        runCurrent()
        assertEquals(UiState.Content(mayor), vm.content().mayor)
        assertTrue(vm.content().common.any { it.type == EventType.MINING_FIESTA })
    }

    @Test
    fun `crops that never loaded are unknown, not waiting for upload`() = runTest {
        handler = { request ->
            if (request.url.encodedPath == "/v1/contests") {
                respondJson("""{"message":"elitebot down"}""", HttpStatusCode.BadGateway)
            } else {
                liveData(request)
            }
        }
        val vm = liveViewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(null, vm.content().common.single { it.type == EventType.JACOBS_CONTEST }.crops)
    }

    @Test
    fun `a failed mayor load is retried automatically`() = runTest {
        handler = { respondJson("""{"message":"Hypixel down"}""", HttpStatusCode.BadGateway) }
        val vm = liveViewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertTrue(vm.content().mayor is UiState.Error)

        handler = { liveData(it) }
        advanceTimeBy(EventsViewModel.LIVE_RETRY_MILLIS + 1)
        runCurrent()
        assertEquals(UiState.Content(mayor), vm.content().mayor)
    }

    private fun <T : Any> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
