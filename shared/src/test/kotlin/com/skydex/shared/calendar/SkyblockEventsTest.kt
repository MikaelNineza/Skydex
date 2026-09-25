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
        assertEquals(EventType.values().toSet(), events.map { it.type }.toSet())
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

    @Test
    fun rejectsNegativeWindow() {
        assertFailsWith<IllegalArgumentException> { SkyblockEvents.upcoming(0, -1) }
    }
}
