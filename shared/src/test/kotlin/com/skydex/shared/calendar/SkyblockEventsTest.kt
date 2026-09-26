package com.skydex.shared.calendar

import com.skydex.shared.calendar.SkyblockCalendar.DAY_MILLIS
import com.skydex.shared.calendar.SkyblockCalendar.YEAR_MILLIS
import com.skydex.shared.model.EventType
import com.skydex.shared.model.SkyblockEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val MINUTE = 60 * 1000L
private const val HOUR = 60 * MINUTE

class SkyblockEventsTest {
    private fun of(type: EventType, events: List<SkyblockEvent>) = events.filter { it.type == type }

    @Test
    fun everyTypeAppearsWithinAYear() {
        val events = SkyblockEvents.upcoming(SkyblockDate(300, 1, 1).toMillis(), YEAR_MILLIS)
        // Perk-gated events need an active mayor, and the Year-of events only come every 12 years.
        val excluded = setOf(
            EventType.FISHING_FESTIVAL,
            EventType.MINING_FIESTA,
            EventType.YEAR_OF_THE_SEAL,
            EventType.YEAR_OF_THE_WITCH,
            EventType.YEAR_OF_THE_PIG,
        )
        assertEquals(EventType.values().toSet() - excluded, events.map { it.type }.toSet())
    }

    @Test
    fun resultIsSortedAndInsideWindow() {
        val now = SkyblockDate(300, 11, 20, 13, 7).toMillis() + 123
        val window = YEAR_MILLIS
        val events = SkyblockEvents.upcoming(now, window)
        assertEquals(events.sortedBy { it.startsAt }, events)
        for (e in events) {
            assertTrue(e.startsAt < now + window, "$e starts after window")
            assertTrue(e.endsAt > now || e.startsAt == now, "$e already over")
        }
    }

    @Test
    fun yearlyFestivals() {
        val events = SkyblockEvents.upcoming(SkyblockDate(300, 1, 1).toMillis(), YEAR_MILLIS)
        fun single(type: EventType, from: SkyblockDate, until: SkyblockDate) =
            assertEquals(listOf(SkyblockEvent(type, from.toMillis(), until.toMillis())), of(type, events))
        single(EventType.SPOOKY_FESTIVAL, SkyblockDate(300, 8, 29), SkyblockDate(300, 9, 1))
        single(EventType.SEASON_OF_JERRY, SkyblockDate(300, 12, 24), SkyblockDate(300, 12, 27))
        single(EventType.NEW_YEAR_CELEBRATION, SkyblockDate(300, 12, 29), SkyblockDate(301, 1, 1))
        val zoo = of(EventType.TRAVELING_ZOO, events)
        val zooDays = zoo.map { SkyblockDate.fromMillis(it.startsAt) to SkyblockDate.fromMillis(it.endsAt) }
        assertEquals(
            listOf(
                SkyblockDate(300, 4, 1) to SkyblockDate(300, 4, 4),
                SkyblockDate(300, 10, 1) to SkyblockDate(300, 10, 4),
            ),
            zooDays,
        )
        assertEquals(3 * DAY_MILLIS, of(EventType.SPOOKY_FESTIVAL, events).single().let { it.endsAt - it.startsAt })
    }

    @Test
    fun cultOfTheFallenStar() {
        val now = SkyblockDate(10, 3, 1).toMillis()
        val events = of(EventType.CULT_OF_THE_FALLEN_STAR, SkyblockEvents.upcoming(now, 31 * DAY_MILLIS))
        val expected = listOf(7, 14, 21, 28).map { SkyblockDate(10, 3, it) }
        assertEquals(expected, events.map { SkyblockDate.fromMillis(it.startsAt) })
        events.forEach { assertEquals(5 * MINUTE, it.endsAt - it.startsAt) }
    }

    @Test
    fun jacobAndDarkAuctionMatchRealTimeMinutes() {
        // 2026-09-25 00:00 UTC.
        val now = 1_790_294_400_000L
        val events = SkyblockEvents.upcoming(now, 24 * HOUR)
        val jacob = of(EventType.JACOBS_CONTEST, events)
        val da = of(EventType.DARK_AUCTION, events)
        assertEquals(24, jacob.size)
        assertEquals(24, da.size)
        jacob.forEach {
            assertEquals(15 * MINUTE, it.startsAt % HOUR)
            assertEquals(20 * MINUTE, it.endsAt - it.startsAt)
        }
        da.forEach { assertEquals(55 * MINUTE, it.startsAt % HOUR) }
        // Contest times published by api.elitebot.dev for Year 516.
        val published = 1_790_176_500_000L
        val around = of(EventType.JACOBS_CONTEST, SkyblockEvents.upcoming(published, 0))
        assertEquals(listOf(SkyblockEvent(EventType.JACOBS_CONTEST, published, published + 20 * MINUTE)), around)
    }

    @Test
    fun bankInterestIsInstantEverySeason() {
        val now = SkyblockDate(516, 1, 1).toMillis()
        val events = of(EventType.BANK_INTEREST, SkyblockEvents.upcoming(now, YEAR_MILLIS))
        val expected = listOf(1, 4, 7, 10).map { SkyblockDate(516, it, 1) }
        assertEquals(expected, events.map { SkyblockDate.fromMillis(it.startsAt) })
        events.forEach { assertEquals(it.startsAt, it.endsAt) }
        // inventivetalent's bank interest timer estimate for Year 516 Early Autumn.
        assertTrue(1_790_394_900_000L in events.map { it.startsAt })
    }

    @Test
    fun ongoingEventIsIncluded() {
        val now = SkyblockDate(300, 8, 30, 12, 0).toMillis()
        val spooky = of(EventType.SPOOKY_FESTIVAL, SkyblockEvents.upcoming(now, 0)).single()
        assertEquals(SkyblockDate(300, 8, 29).toMillis(), spooky.startsAt)
    }

    @Test
    fun windowEdges() {
        val start = SkyblockDate(300, 8, 29).toMillis()
        val end = SkyblockDate(300, 9, 1).toMillis()
        fun spooky(now: Long, window: Long) = of(EventType.SPOOKY_FESTIVAL, SkyblockEvents.upcoming(now, window))
        // Window end is exclusive.
        assertEquals(0, spooky(start - HOUR, HOUR).size)
        assertEquals(1, spooky(start - HOUR, HOUR + 1).size)
        // Running exactly at its start, not at its (exclusive) end.
        assertEquals(1, spooky(start, 0).size)
        assertEquals(1, spooky(end - 1, 0).size)
        assertEquals(0, spooky(end, 0).size)
        // Instantaneous bank interest counts only at its exact instant.
        val interest = SkyblockDate(300, 4, 1).toMillis()
        fun bank(now: Long, window: Long) = of(EventType.BANK_INTEREST, SkyblockEvents.upcoming(now, window))
        assertEquals(1, bank(interest, 0).size)
        assertEquals(0, bank(interest + 1, 0).size)
        assertEquals(0, bank(interest - 10, 10).size)
        assertEquals(1, bank(interest - 10, 11).size)
    }

    // Term won in the Year 515 election: Late Spring 27th, 516 until Late Spring 27th, 517.
    private val term = termBounds(515)
    private val fiestaPerks = ActivePerks(miningFiesta = true, termStartsAt = term.first, termEndsAt = term.second)
    private val fishingPerks = ActivePerks(fishingFestival = true, termStartsAt = term.first, termEndsAt = term.second)

    private fun starts(events: List<SkyblockEvent>) = events.map { SkyblockDate.fromMillis(it.startsAt) }

    @Test
    fun specialYearsComeEveryTwelveYears() {
        fun whole(type: EventType, year: Int) {
            val occurrence = SkyblockEvents.occurrences(type, SkyblockDate(year - 1, 6, 1).toMillis(), 1).single()
            val expected = SkyblockEvent(type, SkyblockDate(year, 1, 1).toMillis(), SkyblockDate(year + 1, 1, 1).toMillis())
            assertEquals(expected, occurrence)
        }
        whole(EventType.YEAR_OF_THE_SEAL, 522)
        whole(EventType.YEAR_OF_THE_SEAL, 534)
        whole(EventType.YEAR_OF_THE_WITCH, 524)
        whole(EventType.YEAR_OF_THE_WITCH, 536)
        whole(EventType.YEAR_OF_THE_PIG, 527)
        whole(EventType.YEAR_OF_THE_PIG, 539)
        // Running all through its year, and nothing in between: the Seal after 522 is 534.
        val seals = SkyblockEvents.occurrences(EventType.YEAR_OF_THE_SEAL, SkyblockDate(522, 12, 31).toMillis(), 2)
        assertEquals(listOf(SkyblockDate(522, 1, 1), SkyblockDate(534, 1, 1)), starts(seals))
    }

    @Test
    fun electionBoothAndTermChange() {
        val booth = SkyblockEvents.occurrences(EventType.ELECTION_OPEN, SkyblockDate(516, 1, 1).toMillis(), 1).single()
        // Booth of the previous election is still open on Early Spring 1st, 516.
        assertEquals(SkyblockDate(515, 6, 27).toMillis(), booth.startsAt)
        assertEquals(SkyblockDate(516, 3, 27).toMillis(), booth.endsAt)
        val next = SkyblockEvents.occurrences(EventType.ELECTION_OPEN, SkyblockDate(516, 4, 1).toMillis(), 1).single()
        assertEquals(SkyblockDate(516, 6, 27).toMillis(), next.startsAt)
        assertEquals(SkyblockDate(517, 3, 27).toMillis(), next.endsAt)

        val change = SkyblockEvents.occurrences(EventType.MAYOR_TERM_CHANGE, SkyblockDate(516, 1, 1).toMillis(), 2)
        assertEquals(listOf(SkyblockDate(516, 3, 27), SkyblockDate(517, 3, 27)), starts(change))
        change.forEach { assertEquals(it.startsAt, it.endsAt) }
        assertEquals(term.first, change[0].startsAt)
        assertEquals(term.second, change[1].startsAt)
    }

    @Test
    fun hoppityAndJerrysWorkshop() {
        val now = SkyblockDate(300, 1, 1).toMillis()
        val hoppity = SkyblockEvents.occurrences(EventType.HOPPITYS_HUNT, now, 1).single()
        assertEquals(SkyblockDate(300, 1, 1).toMillis(), hoppity.startsAt)
        assertEquals(SkyblockDate(300, 4, 1).toMillis(), hoppity.endsAt)

        val workshop = SkyblockEvents.occurrences(EventType.JERRYS_WORKSHOP, now, 1).single()
        assertEquals(SkyblockDate(300, 12, 1).toMillis(), workshop.startsAt)
        assertEquals(SkyblockDate(301, 1, 1).toMillis(), workshop.endsAt)
    }

    @Test
    fun perkEventsNeedTheirPerk() {
        val now = SkyblockDate(516, 4, 1).toMillis()
        for (type in listOf(EventType.MINING_FIESTA, EventType.FISHING_FESTIVAL)) {
            assertEquals(emptyList(), SkyblockEvents.occurrences(type, now, 10))
            assertEquals(emptyList(), of(type, SkyblockEvents.upcoming(now, YEAR_MILLIS)))
        }
        // One perk doesn't imply the other.
        assertEquals(emptyList(), SkyblockEvents.occurrences(EventType.FISHING_FESTIVAL, now, 10, fiestaPerks))
        assertEquals(emptyList(), SkyblockEvents.occurrences(EventType.MINING_FIESTA, now, 10, fishingPerks))
    }

    @Test
    fun miningFiestaFiveTimesPerTermOnlyInsideIt() {
        val from = SkyblockDate(515, 1, 1).toMillis()
        val fiestas = SkyblockEvents.occurrences(EventType.MINING_FIESTA, from, 50, fiestaPerks)
        val expected = listOf(
            SkyblockDate(516, 4, 1), SkyblockDate(516, 6, 1), SkyblockDate(516, 8, 1),
            SkyblockDate(516, 10, 1), SkyblockDate(517, 2, 1),
        )
        assertEquals(expected, starts(fiestas))
        fiestas.forEach {
            assertEquals(7 * DAY_MILLIS, it.endsAt - it.startsAt)
            assertTrue(it.startsAt >= term.first && it.startsAt < term.second)
        }
        val window = of(EventType.MINING_FIESTA, SkyblockEvents.upcoming(from, 3 * YEAR_MILLIS, fiestaPerks))
        assertEquals(fiestas, window)
        assertEquals(emptyList(), SkyblockEvents.occurrences(EventType.MINING_FIESTA, term.second, 5, fiestaPerks))
    }

    @Test
    fun fishingFestivalMonthlyInsideTerm() {
        val festivals = SkyblockEvents.occurrences(EventType.FISHING_FESTIVAL, 0, 100, fishingPerks)
        assertEquals(12, festivals.size)
        assertEquals(SkyblockDate(516, 4, 1), starts(festivals).first())
        assertEquals(SkyblockDate(517, 3, 1), starts(festivals).last())
        festivals.forEach { assertEquals(3 * DAY_MILLIS, it.endsAt - it.startsAt) }
        val during = SkyblockDate(516, 5, 2).toMillis()
        val running = of(EventType.FISHING_FESTIVAL, SkyblockEvents.upcoming(during, 0, fishingPerks)).single()
        assertEquals(SkyblockDate(516, 5, 1).toMillis(), running.startsAt)
    }

    @Test
    fun foxyExtraEventOnSummer22nd() {
        val foxy = ActivePerks(extraEvent = EventType.MINING_FIESTA, termStartsAt = term.first, termEndsAt = term.second)
        val extra = SkyblockEvents.occurrences(EventType.MINING_FIESTA, SkyblockDate(516, 1, 1).toMillis(), 10, foxy)
        val fiesta = SkyblockEvent(
            EventType.MINING_FIESTA,
            SkyblockDate(516, 6, 22).toMillis(),
            SkyblockDate(516, 6, 29).toMillis(),
        )
        assertEquals(listOf(fiesta), extra)
        val window = SkyblockEvents.upcoming(SkyblockDate(516, 6, 1).toMillis(), YEAR_MILLIS, foxy)
        assertEquals(listOf(fiesta), of(EventType.MINING_FIESTA, window))

        val spooky = ActivePerks(extraEvent = EventType.SPOOKY_FESTIVAL, termStartsAt = term.first, termEndsAt = term.second)
        val spookies = SkyblockEvents.occurrences(EventType.SPOOKY_FESTIVAL, SkyblockDate(516, 4, 1).toMillis(), 2, spooky)
        assertEquals(listOf(SkyblockDate(516, 6, 22), SkyblockDate(516, 8, 29)), starts(spookies))
        assertEquals(3 * DAY_MILLIS, spookies[0].endsAt - spookies[0].startsAt)
        // Once over, the extra one is gone.
        val later = SkyblockEvents.occurrences(EventType.SPOOKY_FESTIVAL, SkyblockDate(516, 7, 1).toMillis(), 2, spooky)
        assertEquals(listOf(SkyblockDate(516, 8, 29), SkyblockDate(517, 8, 29)), starts(later))
    }

    @Test
    fun occurrencesReturnsExactlyCountSortedIncludingRunning() {
        val during = SkyblockDate(300, 4, 2).toMillis()
        val zoo = SkyblockEvents.occurrences(EventType.TRAVELING_ZOO, during, 3)
        assertEquals(listOf(SkyblockDate(300, 4, 1), SkyblockDate(300, 10, 1), SkyblockDate(301, 4, 1)), starts(zoo))

        for (type in EventType.entries - setOf(EventType.MINING_FIESTA, EventType.FISHING_FESTIVAL)) {
            val list = SkyblockEvents.occurrences(type, during, 10)
            assertEquals(10, list.size, "$type")
            assertEquals(list.sortedBy { it.startsAt }, list, "$type")
            assertTrue(list.all { it.type == type && (it.endsAt > during || it.startsAt == during) }, "$type")
        }
        assertEquals(emptyList(), SkyblockEvents.occurrences(EventType.DARK_AUCTION, during, 0))
        assertFailsWith<IllegalArgumentException> { SkyblockEvents.occurrences(EventType.DARK_AUCTION, during, -1) }
    }

    @Test
    fun occurrencesAgreeWithUpcoming() {
        val now = SkyblockDate(516, 5, 3, 7, 30).toMillis() + 17
        val upcoming = SkyblockEvents.upcoming(now, 2 * DAY_MILLIS, fiestaPerks)
        for (type in EventType.entries) {
            val expected = of(type, upcoming)
            if (expected.isEmpty()) continue
            assertEquals(expected, SkyblockEvents.occurrences(type, now, expected.size, fiestaPerks), "$type")
        }
    }

    @Test
    fun rejectsNegativeWindow() {
        assertFailsWith<IllegalArgumentException> { SkyblockEvents.upcoming(0, -1) }
    }
}
